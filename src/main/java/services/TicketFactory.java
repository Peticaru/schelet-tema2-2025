package services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import models.*;

public class TicketFactory {
    private static final ObjectMapper mapper = new ObjectMapper();

    public static Ticket createTicket(JsonNode params, int id, String timestamp) {
        String type = params.get("type").asText();
        Ticket ticket;

        // Create specific instance
        switch (type) {
            case "BUG":
                BugTicket bug = new BugTicket();
                if (params.has("expectedBehavior")) bug.setExpectedBehavior(params.get("expectedBehavior").asText());
                if (params.has("actualBehavior")) bug.setActualBehavior(params.get("actualBehavior").asText());
                if (params.has("frequency")) bug.setFrequency(Frequency.valueOf(params.get("frequency").asText()));
                if (params.has("severity")) bug.setSeverity(Severity.valueOf(params.get("severity").asText()));
                if (params.has("environment")) bug.setEnvironment(params.get("environment").asText());
                if (params.has("errorCode")) bug.setErrorCode(params.get("errorCode").asInt());
                ticket = bug;
                break;
            case "FEATURE_REQUEST":
                FeatureRequestTicket fr = new FeatureRequestTicket();
                if (params.has("businessValue")) fr.setBusinessValue(BusinessValue.valueOf(params.get("businessValue").asText()));
                if (params.has("customerDemand")) fr.setCustomerDemand(CustomerDemand.valueOf(params.get("customerDemand").asText()));
                ticket = fr;
                break;
            case "UI_FEEDBACK":
                UiFeedbackTicket ui = new UiFeedbackTicket();
                if (params.has("uiElementId")) ui.setUiElementId(params.get("uiElementId").asText());
                if (params.has("businessValue")) ui.setBusinessValue(BusinessValue.valueOf(params.get("businessValue").asText()));
                if (params.has("usabilityScore")) ui.setUsabilityScore(params.get("usabilityScore").asInt());
                if (params.has("screenshotUrl")) ui.setScreenshotUrl(params.get("screenshotUrl").asText());
                if (params.has("suggestedFix")) ui.setSuggestedFix(params.get("suggestedFix").asText());
                ticket = ui;
                break;
            default:
                throw new IllegalArgumentException("Unknown ticket type: " + type);
        }

        // Set common fields
        ticket.setId(id);
        ticket.setType(type);
        ticket.setCreatedAt(timestamp);
        ticket.setStatus(Status.OPEN); // Initial status

        if (params.has("title")) ticket.setTitle(params.get("title").asText());
        if (params.has("description")) ticket.setDescription(params.get("description").asText());
        if (params.has("expertiseArea")) ticket.setExpertiseArea(ExpertiseArea.valueOf(params.get("expertiseArea").asText()));
        if (params.has("businessPriority")) ticket.setBusinessPriority(Priority.valueOf(params.get("businessPriority").asText()));

        // Handling reportedBy
        if (params.has("reportedBy")) {
            ticket.setReportedBy(params.get("reportedBy").asText());
        } else {
            ticket.setReportedBy(""); // Default empty for anonymous if missing
        }

        return ticket;
    }
}