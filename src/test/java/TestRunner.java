import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.puppycrawl.tools.checkstyle.Checker;
import com.puppycrawl.tools.checkstyle.ConfigurationLoader;
import com.puppycrawl.tools.checkstyle.PropertiesExpander;
import com.puppycrawl.tools.checkstyle.api.CheckstyleException;
import com.puppycrawl.tools.checkstyle.api.Configuration;
import main.App;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.xml.sax.InputSource;

import java.io.File;
import java.io.IOException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.*;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;

@ExtendWith(TestCaseWatcher.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TestRunner {
    private static final ObjectMapper objectMapper = new ObjectMapper(
            new JsonFactory().enable(JsonParser.Feature.INCLUDE_SOURCE_IN_LOCATION)
    );

    private static final List<DevmindResult> devmindResults = new ArrayList<>();

    public static final String PASSED = "PASSED";

    public static Stream<Arguments> data() {
        return Stream.of(
                Arguments.of("01 - Report", "input/in_01_test_report.json", "out/out_01_test_report.json", "ref/ref_01_test_report.json", 2),
                Arguments.of("02 - Milestone", "input/in_02_test_milestone.json", "out/out_02_test_milestone.json", "ref/ref_02_test_milestone.json", 2),
                Arguments.of("03 - MilestoneEdgeCase", "input/in_03_test_milestone_edge_case.json", "out/out_03_test_milestone_edge_case.json", "ref/ref_03_test_milestone_edge_case.json", 2),
                Arguments.of("04 - Assign", "input/in_04_test_assign.json", "out/out_04_test_assign.json", "ref/ref_04_test_assign.json", 2),
                Arguments.of("05 - AssignEdgeCase", "input/in_05_test_assign_edge_case.json", "out/out_05_test_assign_edge_case.json", "ref/ref_05_test_assign_edge_case.json", 6),
                Arguments.of("06 - Comment", "input/in_06_test_comment.json", "out/out_06_test_comment.json", "ref/ref_06_test_comment.json", 2),
                Arguments.of("07 - CommentEdgeCase", "input/in_07_test_comment_edge_case.json", "out/out_07_test_comment_edge_case.json", "ref/ref_07_test_comment_edge_case.json", 3),
                Arguments.of("08 - StatusChange", "input/in_08_test_status_change.json", "out/out_08_test_status_change.json", "ref/ref_08_test_status_change.json", 3),
                Arguments.of("09 - StatusUndoChange", "input/in_09_test_status_undo_change.json", "out/out_09_test_status_undo_change.json", "ref/ref_09_test_status_undo_change.json", 3),
                Arguments.of("10 - StatusEdgeCase", "input/in_10_test_status_edge_case.json", "out/out_10_test_status_edge_case.json", "ref/ref_10_test_status_edge_case.json", 4),
                Arguments.of("11 - Search", "input/in_11_test_search.json", "out/out_11_test_search.json", "ref/ref_11_test_search.json", 6),
                Arguments.of("12 - Notifications", "input/in_12_test_notifications.json", "out/out_12_test_notifications.json", "ref/ref_12_test_notifications.json", 6),
                Arguments.of("13 - MetricsCustomerImpact", "input/in_13_test_metrics_customer_impact.json", "out/out_13_test_metrics_customer_impact.json", "ref/ref_13_test_metrics_customer_impact.json", 3),
                Arguments.of("14 - MetricsTicketRisk", "input/in_14_test_metrics_ticket_risk.json", "out/out_14_test_metrics_ticket_risk.json", "ref/ref_14_test_metrics_ticket_risk.json", 3),
                Arguments.of("15 - MetricsEfficiency", "input/in_15_test_metrics_efficiency.json", "out/out_15_test_metrics_efficiency.json", "ref/ref_15_test_metrics_efficiency.json", 3),
                Arguments.of("16 - Stability", "input/in_16_test_stability.json", "out/out_16_test_stability.json", "ref/ref_16_test_stability.json", 3),
                Arguments.of("17 - Performance", "input/in_17_test_performance.json", "out/out_17_test_performance.json", "ref/ref_17_test_performance.json", 5),
                Arguments.of("18 - Complex", "input/in_18_test_complex.json", "out/out_18_test_complex.json", "ref/ref_18_test_complex.json", 10),
                Arguments.of("19 - ComplexEdgeCase", "input/in_19_test_complex_edge_case.json", "out/out_19_test_complex_edge_case.json", "ref/ref_19_test_complex_edge_case.json", 12)
        );
    }

    @ParameterizedTest(name = "Test {0}")
    @MethodSource("data")
    public void run(
            final String testName,
            final String inputPath,
            final String outputPath,
            final String refPath,
            final int points
    ) throws IOException {
        App.run(inputPath, outputPath);

        JsonNode outputJson = objectMapper.readTree(new File(outputPath));
        JsonNode refJson = objectMapper.readTree(new File(refPath));

        try {
            assertThatJson(outputJson).isEqualTo(refJson);
            devmindResults.add(new DevmindResult(
                testName,
                PASSED,
                points
            ));
        } catch (AssertionError e) {
            devmindResults.add(new DevmindErrorResult(
                    testName,
                    points,
                    e.getMessage()
            ));
            throw e;
        }

    }

    @Test
    public void testCheckstyle() throws CheckstyleException, IOException {
        File configFile = new File("src/test/resources/checkstyle/checkstyle.xml");
        Configuration config = ConfigurationLoader.loadConfiguration(
                new InputSource(configFile.getAbsolutePath()),
                new PropertiesExpander(System.getProperties()),
                ConfigurationLoader.IgnoredModulesOptions.EXECUTE
        );

        CheckstyleAuditListener checkstyleAuditListener = new CheckstyleAuditListener();
        Checker checker = new Checker();
        checker.setModuleClassLoader(CheckerConstants.class.getClassLoader());
        checker.addListener(checkstyleAuditListener);
        checker.configure(config);

        List<File> files = SourceFileCollector.getJavaSourceFiles("src/main/java");
        int errorCount = checker.process(files);
        checker.destroy();

        try {
            assertThat(errorCount).isLessThanOrEqualTo(CheckerConstants.MAXIMUM_ERROR_CHECKSTYLE);
            devmindResults.add(new DevmindResult(
                    "checkstyle",
                    PASSED,
                    CheckerConstants.CHECKSTYLE_POINTS
            ));
        }
        catch (AssertionError e) {
            devmindResults.add(new DevmindErrorResult(
                    "checkstyle",
                    10,
                    e.getMessage()
            ));
            throw new CheckstyleException(checkstyleAuditListener.toString());
        }
    }

    @Test
    public void testGitCommits() throws GitAPIException, IOException {
        File repoDirectory = new File("./");

        try (Git git = Git.open(repoDirectory)) {
            List<RevCommit> commits = StreamSupport.stream(git.log().call().spliterator(), false)
                    .filter(this::hasNonDeveloperAuthor)
                    .sorted(Comparator.comparing(RevCommit::getCommitTime))
                    .toList();

            assertThat(commits).isNotNull();
            assertThat(commits.size())
                    .isGreaterThanOrEqualTo(CheckerConstants.GIT_MINIMUM_COMMITS);

            devmindResults.add(new DevmindResult(
                    "git",
                    PASSED,
                    CheckerConstants.GIT_POINTS
            ));
        } catch (IOException | AssertionError | GitAPIException e) {
            devmindResults.add(new DevmindErrorResult(
                    "git",
                    CheckerConstants.GIT_POINTS,
                    e.getMessage()
            ));
            throw e;
        }
    }

    @AfterAll
    public static void afterAll() throws JsonProcessingException {
        boolean isDevmindEnvironment = Optional.ofNullable(System.getProperty("environment"))
                .map(value -> value.equals("devmind"))
                .orElse(false);

        if (isDevmindEnvironment) {
            printDevmindResults();
        } else {
            printLocalResults();
        }
    }

    private static void printLocalResults() {
        System.out.println(TestCaseWatcher.stringBuilder);
        System.out.println("Total: " + TestCaseWatcher.totalPoints + "/100");

        boolean allPassed = devmindResults.stream()
                .allMatch(result -> PASSED.equals(result.getStatus()));

        if (allPassed) {
            System.out.println("Well done. You're the GREATEST!");
            System.out.println("https://www.youtube.com/shorts/CZSpfzxga9g");
        }
    }

    private static void printDevmindResults() throws JsonProcessingException {
        System.out.println("BEGIN-DEVMIND-TEST-RESULTS");
        System.out.println(objectMapper
                .writerWithDefaultPrettyPrinter()
                .writeValueAsString(devmindResults)
        );
        System.out.println("END-DEVMIND-TEST-RESULTS");
    }

    private boolean hasNonDeveloperAuthor(RevCommit commit) {
        List<String> exceptedAuthors = List.of(
                "david.capragiu@gmail.com",
                "63539529+Dievaid@users.noreply.github.com",
                "gabrielvalentine738@gmail.com",
                "119312368+gabriel-2802@users.noreply.github.com",
                "deividcapragiu@gmail.com",
                "42833908+luis6156@users.noreply.github.com"
        );

        String userEmail = commit.getAuthorIdent().getEmailAddress();
        return !exceptedAuthors.contains(userEmail);
    }
}
