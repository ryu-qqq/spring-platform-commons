package com.ryuqqq.platform.architecture.support;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;

/** Imports production classes from a sibling Gradle module's {@code build/classes/java/main}. */
public final class ModuleClasses {

    private ModuleClasses() {}

    public static JavaClasses importProductionClasses(String modulePath) {
        Path classesDir = repoRoot().resolve(modulePath).resolve("build/classes/java/main");
        if (!Files.isDirectory(classesDir)) {
            return new ClassFileImporter()
                    .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                    .importPaths(Collections.emptyList());
        }
        return new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPath(classesDir);
    }

    private static Path repoRoot() {
        Path cwd = Paths.get(System.getProperty("user.dir"));
        if ("architecture-tests".equals(cwd.getFileName().toString())) {
            return cwd.getParent();
        }
        return cwd;
    }
}
