package dev.satherov.nexus.gametest.internal.client;

import lombok.SneakyThrows;

import net.minecraft.resources.Identifier;

import com.google.errorprone.annotations.CanIgnoreReturnValue;

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
    /// The nanos the report was opened at.
    /// The suite's time is measured from it.
    ///
    private final long started = System.nanoTime();
    
    ///
    /// Opens an empty report over the file, which nothing writes until [#write()].
    ///
    /// @param file The file the report is written to.
    ///
    /// @throws ParserConfigurationException If no document builder can be created.
    ///
    @SneakyThrows(ParserConfigurationException.class) // This causes the Javadoc to complain, but we're just gonna ignore that
    public TestReport(Path file) {
        this.file = file;
        this.document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        this.suite = this.document.createElement("testsuite");
        
        // Vanilla nests the suite in a second element of the same name, don't ask.
        Element root = this.document.createElement("testsuite");
        root.appendChild(this.suite);
        this.document.appendChild(root);
        this.suite.setAttribute("timestamp", DateTimeFormatter.ISO_INSTANT.format(Instant.now()));
    }
    
    ///
    /// Records the test with the given identifier as passed, having taken the given number of milliseconds.
    ///
    /// @param id     The identifier of the test.
    /// @param millis The milliseconds the test took.
    ///
    public void passed(Identifier id, long millis) {
        this.testCase(id, millis);
    }
    
    ///
    /// Records the test as having failed, either as `failure` if the test is marked as `required` or
    /// as `skipped` if it is not.
    ///
    /// @param id       The identifier of the test.
    /// @param millis   The milliseconds the test took.
    /// @param failure  The failure that ended the test.
    /// @param required If the test is required for the run to pass.
    ///
    public void failed(Identifier id, long millis, Throwable failure, boolean required) {
        Element result = this.document.createElement(required ? "failure" : "skipped");
        result.setAttribute("message", Objects.requireNonNullElseGet(failure.getMessage(), failure::toString));
        this.testCase(id, millis).appendChild(result);
    }
    
    ///
    /// The element representing the test case, where `classname` is the namespace of the mod that declares it.
    ///
    /// @param id     The identifier of the test.
    /// @param millis The milliseconds the test took.
    ///
    /// @return The element of the test case, which is added to the suite.
    ///
    @CanIgnoreReturnValue
    private Element testCase(Identifier id, long millis) {
        Element testCase = this.document.createElement("testcase");
        testCase.setAttribute("name", id.toString());
        testCase.setAttribute("classname", id.getNamespace());
        testCase.setAttribute("time", String.valueOf(millis / 1000.0D));
        this.suite.appendChild(testCase);
        return testCase;
    }
    
    ///
    /// Writes the report to a file, creating all missing directories.
    ///
    /// @throws IOException          If the directory the report is written in could not be created.
    /// @throws TransformerException If the report could not be written to a file.
    ///
    @SneakyThrows({ IOException.class, TransformerException.class }) // yeah same thing here...
    public void write() {
        this.suite.setAttribute("time", String.valueOf(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - this.started) / 1000.0D));
        Files.createDirectories(this.file.toAbsolutePath().getParent());
        TransformerFactory.newInstance().newTransformer().transform(new DOMSource(this.document), new StreamResult(this.file.toFile()));
    }
}
