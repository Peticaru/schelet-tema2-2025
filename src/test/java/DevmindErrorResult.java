import com.fasterxml.jackson.annotation.JsonProperty;

public class DevmindErrorResult extends DevmindResult {
    @JsonProperty
    private final String error;

    public DevmindErrorResult(String test, int testScore, String error) {
        super(test, "ERROR", testScore);
        this.error = error;
    }
}
