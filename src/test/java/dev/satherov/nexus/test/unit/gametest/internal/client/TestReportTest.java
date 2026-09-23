package dev.satherov.nexus.test.unit.gametest.internal.client;

import dev.satherov.nexus.gametest.internal.client.TestReport;

import net.minecraft.resources.Identifier;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.IntStream;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

///
/// Checks the shape of the XML report that [TestReport] writes.
///
public class TestReportTest {
    
    private static final Identifier TEST = Identifier.fromNamespaceAndPath("nexus", "report/some_test");
    
    @Test
    public void nestsTheSuiteInASecondTestsuite(@TempDir Path directory) throws IOException, ParserConfigurationException, SAXException {
        Path file = directory.resolve("report.xml");
        TestReport report = new TestReport(file);
        report.passed(TestReportTest.TEST, 10L);
        report.write();
        
        Element root = TestReportTest.read(file);
        Assertions.assertThat(root.getTagName()).isEqualTo("testsuite");
        Assertions.assertThat(TestReportTest.children(root, "testsuite")).hasSize(1);
        Assertions.assertThat(TestReportTest.cases(file)).hasSize(1);
    }
    
    @Test
    public void writesACasePerTest(@TempDir Path directory) throws IOException, ParserConfigurationException, SAXException {
        Path file = directory.resolve("report.xml");
        TestReport report = new TestReport(file);
        report.passed(TestReportTest.TEST, 10L);
        report.failed(Identifier.fromNamespaceAndPath("other", "report/other_test"), 10L, new IllegalStateException("Broke on purpose"), true);
        report.write();
        
        Assertions.assertThat(TestReportTest.cases(file))
                .extracting(test -> test.getAttribute("name"), test -> test.getAttribute("classname"))
                .containsExactlyInAnyOrder(
                        Assertions.tuple("nexus:report/some_test", "nexus"),
                        Assertions.tuple("other:report/other_test", "other")
                );
    }
    
    @Test
    public void writesTheMessageOfARequiredFailure(@TempDir Path directory) throws IOException, ParserConfigurationException, SAXException {
        Path file = directory.resolve("report.xml");
        TestReport report = new TestReport(file);
        report.failed(TestReportTest.TEST, 10L, new IllegalStateException("Broke on purpose"), true);
        report.write();
        
        Assertions.assertThat(TestReportTest.children(TestReportTest.cases(file).getFirst(), "failure"))
                .singleElement()
                .extracting(failure -> failure.getAttribute("message"))
                .isEqualTo("Broke on purpose");
    }
    
    @Test
    public void writesAnOptionalFailureAsSkipped(@TempDir Path directory) throws IOException, ParserConfigurationException, SAXException {
        Path file = directory.resolve("report.xml");
        TestReport report = new TestReport(file);
        report.failed(TestReportTest.TEST, 10L, new IllegalStateException("Broke on purpose"), false);
        report.write();
        
        Element test = TestReportTest.cases(file).getFirst();
        Assertions.assertThat(TestReportTest.children(test, "skipped")).hasSize(1);
        Assertions.assertThat(TestReportTest.children(test, "failure")).isEmpty();
    }
    
    @Test
    public void writesTheFailureAsTextWithoutAMessage(@TempDir Path directory) throws IOException, ParserConfigurationException, SAXException {
        Path file = directory.resolve("report.xml");
        Throwable failure = new IllegalStateException();
        TestReport report = new TestReport(file);
        report.failed(TestReportTest.TEST, 10L, failure, true);
        report.write();
        
        Assertions.assertThat(TestReportTest.children(TestReportTest.cases(file).getFirst(), "failure"))
                .singleElement()
                .extracting(element -> element.getAttribute("message"))
                .isEqualTo(failure.toString());
    }
    
    @Test
    public void writesTheTimeInSeconds(@TempDir Path directory) throws IOException, ParserConfigurationException, SAXException {
        Path file = directory.resolve("report.xml");
        TestReport report = new TestReport(file);
        report.passed(TestReportTest.TEST, 1500L);
        report.write();
        
        Assertions.assertThat(Double.parseDouble(TestReportTest.cases(file).getFirst().getAttribute("time"))).isEqualTo(1.5D);
        Assertions.assertThat(Double.parseDouble(TestReportTest.children(TestReportTest.read(file), "testsuite").getFirst().getAttribute("time"))).isNotNegative();
    }
    
    @Test
    public void createsTheMissingDirectories(@TempDir Path directory) {
        Path file = directory.resolve("missing").resolve("report.xml");
        new TestReport(file).write();
        Assertions.assertThat(file).isRegularFile();
    }
    
    private static Element read(Path file) throws IOException, ParserConfigurationException, SAXException {
        return DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(file.toFile())
                .getDocumentElement();
    }
    
    private static List<Element> cases(Path file) throws IOException, ParserConfigurationException, SAXException {
        return TestReportTest.children(TestReportTest.children(TestReportTest.read(file), "testsuite").getFirst(), "testcase");
    }
    
    private static List<Element> children(Element parent, String tag) {
        NodeList nodes = parent.getChildNodes();
        return IntStream.range(0, nodes.getLength())
                .mapToObj(nodes::item)
                .filter(node -> node instanceof Element element && element.getTagName().equals(tag))
                .map(Element.class::cast)
                .toList();
    }
}
