package dev.satherov.nexus.gametest.internal.client;

import lombok.SneakyThrows;

import net.minecraft.resources.Identifier;

import org.jetbrains.annotations.ApiStatus;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

///
/// The client run's JUnit-like XML report, same shape as vanilla's server report.
///
@ApiStatus.Internal
public final class TestReport {

    ///
    /// The file the report is written to.
    ///
    private final Path file;

    ///
    /// The document the report is built in.
    ///
    private final Document document;

    ///
    /// The `testsuite` element every case is appended to.
    ///
    private final Element suite;

    ///
    /// The nanos the report was opened at; the suite's time is measured from it.
    ///
    private final long started = System.nanoTime();

    @SneakyThrows(ParserConfigurationException.class)
    public TestReport(Path file) {
        this.file = file;
        this.document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        this.suite = this.document.createElement("testsuite");

        // Vanilla nests the suite in a second element of the same name.
        Element root = this.document.createElement("testsuite");
        root.appendChild(this.suite);
        this.document.appendChild(root);
        this.suite.setAttribute("timestamp", DateTimeFormatter.ISO_INSTANT.format(Instant.now()));
    }

    ///
    /// Records the test as passed, having taken the given milliseconds.
    ///
    public void passed(Identifier id, long millis) {
        this.testCase(id, millis);
    }

    ///
    /// Records the test as failed: as a `failure` if it is required, and as a `skipped` if it is not.
    ///
    public void failed(Identifier id, long millis, Throwable failure, boolean required) {
        Element result = this.document.createElement(required ? "failure" : "skipped");
        result.setAttribute("message", Objects.requireNonNullElseGet(failure.getMessage(), failure::toString));
        this.testCase(id, millis).appendChild(result);
    }

    ///
    /// The case of the test, appended to the suite; `classname` is the mod that declares it, where vanilla names the structure.
    ///
    private Element testCase(Identifier id, long millis) {
        Element testCase = this.document.createElement("testcase");
        testCase.setAttribute("name", id.toString());
        testCase.setAttribute("classname", id.getNamespace());
        testCase.setAttribute("time", String.valueOf(millis / 1000.0D));
        this.suite.appendChild(testCase);
        return testCase;
    }

    ///
    /// Writes the report, creating the directory it sits in.
    ///
    @SneakyThrows({ IOException.class, TransformerException.class })
    public void write() {
        this.suite.setAttribute("time", String.valueOf(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - this.started) / 1000.0D));
        Files.createDirectories(this.file.toAbsolutePath().getParent());
        TransformerFactory.newInstance().newTransformer().transform(new DOMSource(this.document), new StreamResult(this.file.toFile()));
    }
}
