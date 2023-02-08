/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2022 Objectionary
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included
 * in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.eolang.speco;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Map;
import jdk.internal.org.jline.utils.Log;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.SystemUtils;
import org.eolang.jucs.ClasspathSource;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.yaml.snakeyaml.Yaml;

/**
 * Packs tests.
 *
 * @since 0.0.1
 */
class SpecoTest {

    /**
     * The number of lines that the command "eoc link" outputs.
     */
    private static final int INTENT = 11;

    /**
     * Path to project test directory.
     */
    private static final Path TESTS = Path.of(
        "src", "test", "resources", "org", "eolang", "speco"
    );

    @Tag("fast")
    @ParameterizedTest
    @ValueSource(strings = {"simple"})
    public void convertsFromXmir(final String title, @TempDir final Path temp) throws IOException {
        final Path base = TESTS.resolve("xmir").resolve(title);
        new Speco(base.resolve("in"), temp, false).exec();
        final Path reference = base.resolve("out");
        for (final Path path : Files.newDirectoryStream(reference)) {
            MatcherAssert.assertThat(
                String.format(
                    "Files %s in %s and %s are different",
                    path.getFileName(),
                    temp,
                    reference
                ),
                Files.readAllLines(temp.resolve(path.getFileName())),
                Matchers.equalTo(
                    Files.readAllLines(path)
                )
            );
        }
    }

    /**
     * Integration test for conversation from EO.
     * @todo #32:90min fix disabled convertsFromEo tests for a group with matrices,
     *  the reason is that only one parameter is processed for specialization,
     *  but it is necessary to process all.
     * @param pack Pack this test data
     * @param temp Temporary test dir
     * @throws IOException Iff IO error
     */
    @Disabled
    @Tag("fast")
    @ParameterizedTest
    @ClasspathSource(value = "org/eolang/speco/packs", glob = "**.yaml")
    public void convertsFromEo(final String pack, @TempDir final Path temp) throws IOException {
        final Map<String, Object> script = new Yaml().load(pack);
        final Path input = TESTS.resolve("input");
        final Path output = temp.resolve("input");
        SpecoTest.run(script, input, output);
        MatcherAssert.assertThat(
            "Unexpected transformation result",
            Files.readString(output.resolve("app.eo")),
            Matchers.equalTo(
                script.get("after").toString()
            )
        );
    }

    @Tag("slow")
    @ParameterizedTest
    @ClasspathSource(value = "org/eolang/speco/packs", glob = "**.yaml")
    public void compilesFromEo(final String pack, @TempDir final Path temp) throws IOException {
        final Map<String, Object> script = new Yaml().load(pack);
        final Path input = TESTS.resolve("input");
        final Path output = temp.resolve("input");
        SpecoTest.run(script, output, temp);
        MatcherAssert.assertThat(
            "Unexpected execution result",
            SpecoTest.dataize(output.toString()),
            Matchers.equalTo(
                script.get("result").toString().split("\\r?\\n")
            )
        );
    }

    /**
     * Runs Speco.
     *
     * @param script Yaml data object
     * @param input Path to the input dir
     * @param output Path to the output dir
     * @throws IOException Iff IO error
     */
    private static void run(final Map<String, Object> script, final Path input, final Path output)
        throws IOException {
        Files.createDirectories(input);
        Files.write(
            input.resolve("app.eo"),
            script.get("before").toString().getBytes(),
            StandardOpenOption.CREATE
        );
        new Speco(input, output, true).exec();
    }

    /**
    * Compiles EO program.
    *
    * @param target Path to the dir with target EO program
    * @return List of lines in output
    * @throws IOException Iff IO error
    */
    private static String[] dataize(final String target) throws IOException {
        final String executor;
        final String flag;
        if (SystemUtils.IS_OS_WINDOWS) {
            executor = "cmd";
            flag = "/c";
        } else {
            executor = "bash";
            flag = "-c";
        }
        final Process process = new ProcessBuilder(
            executor,
            flag,
            String.format("eoc link -s %s && eoc --alone dataize app && eoc clean", target)
        ).start();
        try {
            process.waitFor();
        } catch (final InterruptedException exception) {
            exception.printStackTrace();
        }
        final StringWriter writer = new StringWriter();
        IOUtils.copy(process.getInputStream(), writer, Charset.defaultCharset());
        process.getInputStream().close();
        process.destroy();
        final String[] output = writer.toString().split("\\r?\\n");
        writer.close();
        return Arrays.copyOfRange(output, SpecoTest.INTENT, output.length - 1);
    }
}
