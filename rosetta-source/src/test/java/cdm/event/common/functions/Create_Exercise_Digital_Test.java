package cdm.event.common.functions;

import cdm.base.math.UnitType;
import cdm.event.common.ExerciseInstruction;
import cdm.event.common.PrimitiveInstruction;
import cdm.event.common.TradeState;
import cdm.observable.asset.Money;
import cdm.product.common.settlement.CashSettlementTerms;
import cdm.product.common.settlement.SettlementTerms;
import cdm.product.common.settlement.SettlementTypeEnum;
import cdm.product.template.DigitalOption;
import cdm.product.template.EconomicTerms;
import cdm.product.template.NonTransferableProduct;
import cdm.product.template.OptionFeature;
import cdm.product.template.OptionPayout;
import cdm.product.template.Payout;
import cdm.product.template.metafields.ReferenceWithMetaPayout;
import org.finos.cdm.functions.AbstractFunctionTest;
import org.finos.cdm.util.ResourcesUtils;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class Create_Exercise_Digital_Test extends AbstractFunctionTest {

    private static final String EQUITY_OPTION_FIXTURE =
            "ingest/output/fpml-confirmation-to-trade-state/fpml-5-13-incomplete-products-equity-options/eqd-ex01-american-call-stock-long-form.json";

    @Inject Create_Exercise createExercise;

    @Test
    void digitalCashOrNothing_exerciseProducesReducedTradeOnly() throws IOException {
        TradeState originalTrade = loadAndMutateOption(SettlementTypeEnum.CASH);
        ExerciseInstruction instruction = buildExerciseInstruction(originalTrade);

        List<? extends TradeState> result = createExercise.evaluate(instruction, originalTrade);

        assertEquals(1, result.size(),
                "Digital cash-or-nothing exercise must produce only the reduced original trade (no underlier replacement).");
    }

    @Test
    void digitalAssetOrNothing_exerciseProducesReducedAndReplacementTrades() throws IOException {
        TradeState originalTrade = loadAndMutateOption(SettlementTypeEnum.PHYSICAL);
        ExerciseInstruction instruction = buildExerciseInstruction(originalTrade);

        List<? extends TradeState> result = createExercise.evaluate(instruction, originalTrade);

        assertEquals(2, result.size(),
                "Digital asset-or-nothing exercise must produce reduced original + underlier-delivery replacement.");
    }

    @Test
    void vanillaOption_exerciseProducesReducedAndReplacementTrades() throws IOException {
        TradeState originalTrade = ResourcesUtils.getObjectAndResolveReferences(TradeState.class, EQUITY_OPTION_FIXTURE);
        ExerciseInstruction instruction = buildExerciseInstruction(originalTrade);

        List<? extends TradeState> result = createExercise.evaluate(instruction, originalTrade);

        assertEquals(2, result.size(),
                "Vanilla equity option exercise must still produce reduced + replacement trade.");
    }

    private static TradeState loadAndMutateOption(SettlementTypeEnum settlementType) throws IOException {
        TradeState originalTrade = ResourcesUtils.getObjectAndResolveReferences(TradeState.class, EQUITY_OPTION_FIXTURE);

        OptionPayout originalOptionPayout = originalTrade.getTrade().getProduct().getEconomicTerms()
                .getPayout().get(0).getOptionPayout();
        assertNotNull(originalOptionPayout);

        SettlementTerms.SettlementTermsBuilder settlementTermsBuilder = SettlementTerms.builder()
                .setSettlementType(settlementType);

        if (settlementType == SettlementTypeEnum.CASH) {
            settlementTermsBuilder.addCashSettlementTerms(CashSettlementTerms.builder()
                    .setCashSettlementAmount(Money.builder()
                            .setValue(BigDecimal.valueOf(1_000_000))
                            .setUnit(UnitType.builder().setCurrencyValue("EUR"))));
        }

        OptionPayout mutatedOptionPayout = originalOptionPayout.toBuilder()
                .setFeature(OptionFeature.builder()
                        .setDigitalOption(DigitalOption.builder().setIsApplicable(true)))
                .setSettlementTerms(settlementTermsBuilder)
                .build();

        Payout mutatedPayout = Payout.builder().setOptionPayout(mutatedOptionPayout).build();

        EconomicTerms mutatedEconomicTerms = originalTrade.getTrade().getProduct().getEconomicTerms().toBuilder()
                .setPayout(Collections.singletonList(mutatedPayout))
                .build();

        NonTransferableProduct mutatedProduct = originalTrade.getTrade().getProduct().toBuilder()
                .setEconomicTerms(mutatedEconomicTerms)
                .build();

        return originalTrade.toBuilder()
                .setTrade(originalTrade.getTrade().toBuilder()
                        .setProduct(mutatedProduct))
                .build();
    }

    private static ExerciseInstruction buildExerciseInstruction(TradeState tradeState) {
        OptionPayout fromTrade = tradeState.getTrade().getProduct().getEconomicTerms()
                .getPayout().get(0).getOptionPayout();

        Payout exerciseOptionPayout = Payout.builder().setOptionPayout(fromTrade).build();

        ReferenceWithMetaPayout option = ReferenceWithMetaPayout.builder()
                .setValue(exerciseOptionPayout)
                .build();

        return ExerciseInstruction.builder()
                .setExerciseOptionValue(exerciseOptionPayout)
                .setExerciseQuantity(PrimitiveInstruction.builder().build())
                .setExerciseOption(option)
                .addReplacementTradeIdentifier(tradeState.getTrade().getTradeIdentifier())
                .build();
    }
}
