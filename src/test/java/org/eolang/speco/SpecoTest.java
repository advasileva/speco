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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Map;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.SystemUtils;
import org.eolang.jucs.ClasspathSource;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.io.CleanupMode;
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

    @Tag("fast")
    @ParameterizedTest
    @ValueSource(strings = {"simple"})
    public void convertsFromXmir(
        final String title,
        @TempDir(cleanup = CleanupMode.NEVER) final Path temp) throws IOException {
        final Path base = Path.of(
            "src", "test", "resources",
            "org", "eolang", "speco",
            "xmir", title
        );
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

    @Disabled
    @Tag("fast")
    @ParameterizedTest
    @ClasspathSource(value = "org/eolang/speco/packs", glob = "**.yaml")
    public void convertsFromEo(
        final String pack,
        @TempDir(cleanup = CleanupMode.NEVER) final Path temp) throws IOException {
        final Map<String, Object> script = new Yaml().load(pack);
        MatcherAssert.assertThat(
            "Unexpected transformation result",
            Files.readString(SpecoTest.runSpeco(script, temp).resolve("app.eo")),
            Matchers.equalTo(
                script.get("after").toString()
            )
        );
    }

    @Tag("slow")
    @ParameterizedTest
    @ClasspathSource(value = "org/eolang/speco/packs", glob = "**.yaml")
    public void compilesFromEo(
        final String pack,
        @TempDir(cleanup = CleanupMode.NEVER) final Path temp) throws IOException {
        final Map<String, Object> script = new Yaml().load(pack);
        MatcherAssert.assertThat(
            "Unexpected execution result",
            SpecoTest.exec(SpecoTest.runSpeco(script, temp).toString()),
            Matchers.equalTo(
                script.get("result").toString().split("\\r?\\n")
            )
        );
    }

    /**
     * Runs Speco.
     *
     * @param script Yaml data object
     * @param temp Path to the temporary dir
     * @return Path to the output dir
     * @throws IOException Iff IO error
     */
    private static Path runSpeco(final Map<String, Object> script, final Path temp)
        throws IOException {
        final Path input = temp.resolve("input");
        final Path output = temp.resolve("output");
        Files.createDirectories(input);
        Files.write(
            input.resolve("app.eo"),
            script.get("before").toString().getBytes(),
            StandardOpenOption.CREATE
        );
        new Speco(input, output, true).exec();
        return output;
    }

    /**
    * Compiles EO program.
    *
    * @param target Path to the dir with target EO program
    * @return List of lines in output
    * @throws IOException Iff IO error
    */
    private static String[] exec(final String target) throws IOException {
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
        final StringWriter writer = new StringWriter();
        IOUtils.copy(process.getInputStream(), writer);
        final String[] output = writer.toString().split("\\r?\\n");
        return Arrays.copyOfRange(output, 11, output.length - 1);
    }
}
