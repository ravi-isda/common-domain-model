package cdm.product.qualification.functions;

import cdm.base.staticdata.asset.common.Asset;
import cdm.base.staticdata.asset.common.Cash;
import cdm.event.common.TradeState;
import cdm.observable.asset.Observable;
import cdm.observable.asset.PriceSchedule;
import cdm.observable.asset.PriceTypeEnum;
import cdm.observable.event.TouchConditionEnum;
import cdm.observable.event.TriggerTimeTypeEnum;
import cdm.observable.event.TriggerTypeEnum;
import cdm.product.common.settlement.SettlementTerms;
import cdm.product.common.settlement.SettlementTypeEnum;
import cdm.product.template.DigitalOption;
import cdm.product.template.DigitalTrigger;
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
import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Qualify_ForeignExchange_DigitalOption_Test extends AbstractFunctionTest {

    @Inject Qualify_ForeignExchange_SimpleExotic_Digital qualifyDigital;

    private static final String FX_EX14_EUROPEAN =
            "ingest/output/fpml-confirmation-to-trade-state/fpml-5-13-incomplete-products-fx-derivatives/fx-ex14-euro-digital-option.json";
    private static final String FX_EX16_ONE_TOUCH =
            "ingest/output/fpml-confirmation-to-trade-state/fpml-5-13-incomplete-products-fx-derivatives/fx-ex16-one-touch-option.json";
    private static final String FX_EX17_NO_TOUCH =
            "ingest/output/fpml-confirmation-to-trade-state/fpml-5-13-incomplete-products-fx-derivatives/fx-ex17-no-touch-option.json";

    // --- Qualification: digital marker + settlement type ---

    @Test
    void qualifiesWhenDigitalFeatureAndCashSettlement() {
        EconomicTerms economicTerms = buildFxDigital(SettlementTypeEnum.CASH, null);
        assertTrue(qualifyDigital.evaluate(economicTerms));
    }

    @Test
    void qualifiesWhenDigitalFeatureAndPhysicalSettlement() {
        EconomicTerms economicTerms = buildFxDigital(SettlementTypeEnum.PHYSICAL, null);
        assertTrue(qualifyDigital.evaluate(economicTerms));
    }

    @Test
    void doesNotQualifyForFxSwap() throws IOException {
        EconomicTerms economicTerms = getEconomicTerms(
                "ingest/output/fpml-confirmation-to-trade-state/fpml-5-13-products-fx-derivatives/fx-ex08-fx-swap.json");
        assertFalse(qualifyDigital.evaluate(economicTerms));
    }

    // --- Trigger structure: programmatic construction — European ---

    @Test
    void buildEuropeanDigital_qualifiesAndHasClosingTriggerNoTouch() {
        EconomicTerms economicTerms = buildFxDigital(SettlementTypeEnum.CASH, europeanTrigger("1.4800"));

        assertTrue(qualifyDigital.evaluate(economicTerms));

        DigitalTrigger trigger = firstTrigger(economicTerms);
        assertEquals(0, new BigDecimal("1.4800").compareTo(trigger.getLevel().get(0).getValue()));
        assertEquals(TriggerTypeEnum.EQUAL_OR_GREATER, trigger.getTriggerType());
        assertEquals(TriggerTimeTypeEnum.CLOSING, trigger.getTriggerTimeType());
        assertNull(trigger.getTouchCondition(), "European digital must not carry touchCondition");
    }

    // --- Trigger structure: programmatic construction — American ---

    @Test
    void buildAmericanOneTouchDigital_qualifiesAndHasAnytimeTriggerWithTouch() {
        EconomicTerms economicTerms = buildFxDigital(SettlementTypeEnum.CASH, americanTrigger("1.5200", TouchConditionEnum.TOUCH));

        assertTrue(qualifyDigital.evaluate(economicTerms));

        DigitalTrigger trigger = firstTrigger(economicTerms);
        assertEquals(0, new BigDecimal("1.5200").compareTo(trigger.getLevel().get(0).getValue()));
        assertEquals(TriggerTimeTypeEnum.ANYTIME, trigger.getTriggerTimeType());
        assertEquals(TouchConditionEnum.TOUCH, trigger.getTouchCondition());
    }

    @Test
    void buildAmericanNoTouchDigital_qualifiesAndHasAnytimeTriggerWithNoTouch() {
        EconomicTerms economicTerms = buildFxDigital(SettlementTypeEnum.CASH, americanTrigger("1.4500", TouchConditionEnum.NO_TOUCH));

        assertTrue(qualifyDigital.evaluate(economicTerms));

        DigitalTrigger trigger = firstTrigger(economicTerms);
        assertEquals(TriggerTimeTypeEnum.ANYTIME, trigger.getTriggerTimeType());
        assertEquals(TouchConditionEnum.NO_TOUCH, trigger.getTouchCondition());
    }

    // --- Trigger structure: ingested FpML fixtures ---
    //
    // End-to-end assertions: FpML XML → CDM ingest produces a fully-qualifying FX digital
    // option with the DigitalTrigger structure populated correctly.

    @Test
    void ingestedEuropeanDigital_fx_ex14_qualifiesWithClosingTriggerNoTouch() throws IOException {
        EconomicTerms economicTerms = getEconomicTerms(FX_EX14_EUROPEAN);

        assertTrue(qualifyDigital.evaluate(economicTerms));

        DigitalTrigger trigger = firstTrigger(economicTerms);
        assertEquals(0, new BigDecimal("1.4800").compareTo(trigger.getLevel().get(0).getValue()));
        assertEquals(TriggerTypeEnum.EQUAL_OR_GREATER, trigger.getTriggerType());
        assertEquals(TriggerTimeTypeEnum.CLOSING, trigger.getTriggerTimeType());
        assertNull(trigger.getTouchCondition(), "European digital must not carry touchCondition");
    }

    @Test
    void ingestedAmericanOneTouch_fx_ex16_qualifiesWithAnytimeTriggerAndTouch() throws IOException {
        EconomicTerms economicTerms = getEconomicTerms(FX_EX16_ONE_TOUCH);

        assertTrue(qualifyDigital.evaluate(economicTerms));

        DigitalTrigger trigger = firstTrigger(economicTerms);
        assertEquals(TriggerTimeTypeEnum.ANYTIME, trigger.getTriggerTimeType());
        assertEquals(TouchConditionEnum.TOUCH, trigger.getTouchCondition());
    }

    @Test
    void ingestedAmericanNoTouch_fx_ex17_qualifiesWithAnytimeTriggerAndNoTouch() throws IOException {
        EconomicTerms economicTerms = getEconomicTerms(FX_EX17_NO_TOUCH);

        assertTrue(qualifyDigital.evaluate(economicTerms));

        DigitalTrigger trigger = firstTrigger(economicTerms);
        assertEquals(TriggerTimeTypeEnum.ANYTIME, trigger.getTriggerTimeType());
        assertEquals(TouchConditionEnum.NO_TOUCH, trigger.getTouchCondition());
    }

    // --- Helpers ---

    /**
     * Builds a European FX digital trigger: level + AtOrAbove condition, evaluated at expiry (Closing).
     * touchCondition is not set — that's the semantic marker of European vs American.
     */
    private static DigitalTrigger europeanTrigger(String triggerRate) {
        return DigitalTrigger.builder()
                .addLevel(PriceSchedule.builder()
                        .setValue(new BigDecimal(triggerRate))
                        .setPriceType(PriceTypeEnum.EXCHANGE_RATE))
                .setTriggerType(TriggerTypeEnum.EQUAL_OR_GREATER)
                .setTriggerTimeType(TriggerTimeTypeEnum.CLOSING)
                .build();
    }

    /**
     * Builds an American FX digital trigger: level + AtOrAbove condition, continuously monitored (Anytime),
     * with touchCondition indicating whether payoff fires on touch (Touch) or on avoidance (NoTouch).
     */
    private static DigitalTrigger americanTrigger(String triggerRate, TouchConditionEnum touchCondition) {
        return DigitalTrigger.builder()
                .addLevel(PriceSchedule.builder()
                        .setValue(new BigDecimal(triggerRate))
                        .setPriceType(PriceTypeEnum.EXCHANGE_RATE))
                .setTriggerType(TriggerTypeEnum.EQUAL_OR_GREATER)
                .setTriggerTimeType(TriggerTimeTypeEnum.ANYTIME)
                .setTouchCondition(touchCondition)
                .build();
    }

    /**
     * Constructs a minimal FX digital OptionPayout with a Cash underlier so it passes
     * Qualify_AssetClass_ForeignExchange. If {@code trigger} is non-null it is attached.
     */
    private static EconomicTerms buildFxDigital(SettlementTypeEnum settlementType, DigitalTrigger trigger) {
        DigitalOption.DigitalOptionBuilder digital = DigitalOption.builder().setIsApplicable(true);
        if (trigger != null) {
            digital.addTrigger(trigger);
        }
        return EconomicTerms.builder()
                .addPayout(Payout.builder()
                        .setOptionPayout(OptionPayout.builder()
                                .setFeature(OptionFeature.builder().setDigitalOption(digital))
                                .setSettlementTerms(SettlementTerms.builder().setSettlementType(settlementType))
                                .setUnderlier(Underlier.builder()
                                        .setObservableValue(Observable.builder()
                                                .setAsset(Asset.builder()
                                                        .setCash(Cash.builder()))))))
                .build();
    }

    private static DigitalTrigger firstTrigger(EconomicTerms economicTerms) {
        List<? extends DigitalTrigger> triggers = economicTerms.getPayout().get(0).getOptionPayout()
                .getFeature().getDigitalOption().getTrigger();
        assertNotNull(triggers, "trigger list must be present");
        assertTrue(triggers.size() >= 1, "at least one trigger expected");
        return triggers.get(0);
    }

    private static EconomicTerms getEconomicTerms(String resourceName) throws IOException {
        TradeState tradeState = ResourcesUtils.getObjectAndResolveReferences(TradeState.class, resourceName);
        return tradeState.getTrade().getProduct().getEconomicTerms();
    }
}
