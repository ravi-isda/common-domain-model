package cdm.product.qualification.functions;

import cdm.base.staticdata.asset.common.Asset;
import cdm.base.staticdata.asset.common.AssetClassEnum;
import cdm.base.staticdata.asset.common.Instrument;
import cdm.base.staticdata.asset.common.Security;
import cdm.base.staticdata.asset.common.SecurityTypeEnum;
import cdm.event.common.TradeState;
import cdm.observable.asset.Basket;
import cdm.observable.asset.BasketConstituent;
import cdm.observable.asset.EquityIndex;
import cdm.observable.asset.Index;
import cdm.observable.asset.Observable;
import cdm.product.common.settlement.SettlementTerms;
import cdm.product.common.settlement.SettlementTypeEnum;
import cdm.product.template.DigitalOption;
import cdm.product.template.EconomicTerms;
import cdm.product.template.OptionFeature;
import cdm.product.template.OptionPayout;
import cdm.product.template.Payout;
import cdm.product.template.Underlier;
import org.finos.cdm.functions.AbstractFunctionTest;
import org.finos.cdm.util.ResourcesUtils;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Qualify_EquityOption_Digital_Test extends AbstractFunctionTest {

    @Inject Qualify_EquityOption_Digital_SingleName qualifySingleName;
    @Inject Qualify_EquityOption_Digital_Index qualifyIndex;
    @Inject Qualify_EquityOption_Digital_Basket qualifyBasket;
    @Inject Qualify_EquityOption_PriceReturnBasicPerformance_SingleName qualifyVanillaSingleName;
    @Inject Qualify_EquityOption_PriceReturnBasicPerformance_Index qualifyVanillaIndex;
    @Inject Qualify_EquityOption_PriceReturnBasicPerformance_Basket qualifyVanillaBasket;

    // --- SingleName ---

    @Test
    void singleName_qualifiesWhenDigitalFeature() {
        EconomicTerms economicTerms = buildDigitalOption(singleNameObservable(), SettlementTypeEnum.CASH);
        assertTrue(qualifySingleName.evaluate(economicTerms));
    }

    @Test
    void singleName_qualifiesForPhysicalSettlement() {
        EconomicTerms economicTerms = buildDigitalOption(singleNameObservable(), SettlementTypeEnum.PHYSICAL);
        assertTrue(qualifySingleName.evaluate(economicTerms));
    }

    @Test
    void singleName_doesNotQualifyForVanillaOption() throws IOException {
        EconomicTerms economicTerms = getEconomicTerms(
            "ingest/output/fpml-confirmation-to-trade-state/fpml-5-13-incomplete-products-equity-options/eqd-ex01-american-call-stock-long-form.json");
        assertFalse(qualifySingleName.evaluate(economicTerms));
    }

    // --- Index ---

    @Test
    void index_qualifiesWhenDigitalFeature() {
        EconomicTerms economicTerms = buildDigitalOption(equityIndexObservable(), SettlementTypeEnum.CASH);
        assertTrue(qualifyIndex.evaluate(economicTerms));
    }

    @Test
    void index_doesNotQualifyForSingleNameUnderlier() {
        EconomicTerms economicTerms = buildDigitalOption(singleNameObservable(), SettlementTypeEnum.CASH);
        assertFalse(qualifyIndex.evaluate(economicTerms));
    }

    // --- Basket ---

    @Test
    void basket_qualifiesWhenDigitalFeature() {
        EconomicTerms economicTerms = buildDigitalOption(equityBasketObservable(), SettlementTypeEnum.CASH);
        assertTrue(qualifyBasket.evaluate(economicTerms));
    }

    @Test
    void basket_doesNotQualifyForSingleNameUnderlier() {
        EconomicTerms economicTerms = buildDigitalOption(singleNameObservable(), SettlementTypeEnum.CASH);
        assertFalse(qualifyBasket.evaluate(economicTerms));
    }

    // --- Mutual exclusion with vanilla equity options ---

    @Test
    void digitalOption_doesNotQualifyAsVanillaEquityOptionSingleName() {
        EconomicTerms economicTerms = buildDigitalOption(singleNameObservable(), SettlementTypeEnum.CASH);
        assertFalse(qualifyVanillaSingleName.evaluate(economicTerms));
    }

    @Test
    void digitalOption_doesNotQualifyAsVanillaEquityOptionIndex() {
        EconomicTerms economicTerms = buildDigitalOption(equityIndexObservable(), SettlementTypeEnum.CASH);
        assertFalse(qualifyVanillaIndex.evaluate(economicTerms));
    }

    @Test
    void digitalOption_doesNotQualifyAsVanillaEquityOptionBasket() {
        EconomicTerms economicTerms = buildDigitalOption(equityBasketObservable(), SettlementTypeEnum.CASH);
        assertFalse(qualifyVanillaBasket.evaluate(economicTerms));
    }

    // --- Ingested FpML fixtures ---
    //
    // Verifies the FpML equity digital ingest heuristic: detects <featurePayment> inside
    // <feature><barrier> on an <equityOption>, sets feature.digitalOption.isApplicable = true,
    // and populates settlementTerms.cashSettlementTerms.cashSettlementAmount.
    // Per FpML 4.0 Equity Derivatives Architecture, equity binary/digital options are
    // structurally expressed as barrier options with a fixed featurePayment.

    @Test
    void ingestedBinaryBarrier_eqd_ex10_qualifiesAsDigitalIndex() throws IOException {
        EconomicTerms economicTerms = getEconomicTerms(
            "ingest/output/fpml-confirmation-to-trade-state/fpml-5-13-products-equity-options/eqd-ex10-binary-barrier-long-form.json");
        assertTrue(qualifyIndex.evaluate(economicTerms),
                "eqd-ex10 (SP500 binary barrier, EUR 15M featurePayment) should qualify as equity digital index");
    }

    @Test
    void ingestedBarrierKnockoutRebate_eqd_ex07_qualifiesAsDigitalIndex() throws IOException {
        EconomicTerms economicTerms = getEconomicTerms(
            "ingest/output/fpml-confirmation-to-trade-state/fpml-5-13-products-equity-options/eqd-ex07-barrier-knockout-rebate-long-form.json");
        assertTrue(qualifyIndex.evaluate(economicTerms),
                "eqd-ex07 (barrier knock-out with rebate featurePayment) should qualify as equity digital index");
    }

    // --- Helpers ---

    private static EconomicTerms buildDigitalOption(Observable underlier, SettlementTypeEnum settlementType) {
        return EconomicTerms.builder()
            .addPayout(Payout.builder()
                .setOptionPayout(OptionPayout.builder()
                    .setFeature(OptionFeature.builder()
                        .setDigitalOption(DigitalOption.builder().setIsApplicable(true)))
                    .setSettlementTerms(SettlementTerms.builder()
                        .setSettlementType(settlementType))
                    .setUnderlier(Underlier.builder()
                        .setObservableValue(underlier))))
            .build();
    }

    private static Observable singleNameObservable() {
        return Observable.builder()
            .setAsset(Asset.builder()
                .setInstrument(Instrument.builder()
                    .setSecurity(Security.builder()
                        .setSecurityType(SecurityTypeEnum.EQUITY))));
    }

    private static Observable equityIndexObservable() {
        return Observable.builder()
            .setIndex(Index.builder()
                .setEquityIndex(EquityIndex.builder()
                    .setAssetClass(AssetClassEnum.EQUITY)));
    }

    private static Observable equityBasketObservable() {
        return Observable.builder()
            .setBasket(Basket.builder()
                .addBasketConstituentValue(BasketConstituent.builder()
                    .setObservable(Observable.builder()
                        .setAsset(Asset.builder()
                            .setInstrument(Instrument.builder()
                                .setSecurity(Security.builder()
                                    .setSecurityType(SecurityTypeEnum.EQUITY)))))));
    }

    private static EconomicTerms getEconomicTerms(String resourceName) throws IOException {
        TradeState tradeState = ResourcesUtils.getObjectAndResolveReferences(TradeState.class, resourceName);
        return tradeState.getTrade().getProduct().getEconomicTerms();
    }
}
