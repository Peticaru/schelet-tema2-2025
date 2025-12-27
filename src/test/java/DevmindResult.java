import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;

public class DevmindResult {
    @JsonProperty
    private final String test;

    @Getter
    @JsonProperty
    private final String status;

    @JsonProperty("test_score")
    private final int testScore;

    public DevmindResult(String test, String status, int testScore) {
        this.test = test;
        this.status = status;
        this.testScore = testScore;
    }

    @Override
    public String toString() {
        ObjectMapper mapper = new ObjectMapper();
        try {
            return mapper.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            return e.getMessage();
        }
    }
}
