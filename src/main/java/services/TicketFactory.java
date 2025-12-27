package services;

import commands.CommandInput;
import models.*;

public class TicketFactory {

    public static Ticket createTicket(CommandInput input, int id) {
        String type = input.getType();

        Ticket ticket;
        switch (type) {
            case "BUG":
                ticket = mapBug(input);
                break;
            case "FEATURE_REQUEST":
                ticket = mapFeatureRequest(input);
                break;
            case "UI_FEEDBACK":
                ticket = mapUIFeedback(input);
                break;
            default:
                throw new IllegalArgumentException("Unknown ticket type: " + type);
        }

        ticket.setId(id);
        ticket.setType(type);
        ticket.setTitle(input.getTitle());
        ticket.setDescription(input.getDescription());

        ticket.setReportedBy(input.getReportedBy());
        ticket.setCreatedAt(input.getTimestamp());
        ticket.setStatus(Status.OPEN); // Default status

        return ticket;
    }

    private static Bug mapBug(CommandInput input) {
        Bug bug = new Bug();
        bug.setExpectedBehavior(input.getExpectedBehavior());
        bug.setActualBehavior(input.getActualBehavior());
        bug.setSeverity(input.getSeverity());
        bug.setFrequency(input.getFrequency());
        bug.setEnvironment(input.getEnvironment());
        bug.setErrorCode(input.getErrorCode());
        return bug;
    }

    private static FeatureRequest mapFeatureRequest(CommandInput input) {
        FeatureRequest fr = new FeatureRequest();
        fr.setBusinessValue(input.getBusinessValue());
        fr.setCustomerDemand(input.getCustomerDemand());
        return fr;
    }

    private static UIFeedback mapUIFeedback(CommandInput input) {
        UIFeedback ui = new UIFeedback();
        ui.setUiElementId(input.getUiElementId());
        ui.setUsabilityScore(input.getUsabilityScore());
        ui.setScreenshotUrl(input.getScreenshotUrl());
        ui.setSuggestedFix(input.getSuggestedFix());
        return ui;
    }
}