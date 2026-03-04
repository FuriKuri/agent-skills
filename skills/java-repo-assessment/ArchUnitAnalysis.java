///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS com.tngtech.archunit:archunit:1.4.1
//DEPS org.slf4j:slf4j-nop:2.0.13

import com.tngtech.archunit.core.domain.*;
import com.tngtech.archunit.core.importer.*;
import com.tngtech.archunit.lang.*;
import com.tngtech.archunit.library.metrics.*;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

public class ArchUnitAnalysis {

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: jbang ArchUnitAnalysis.java <classes-dir> [base-package]");
            System.err.println("  <classes-dir>   Path to compiled .class files (e.g., target/classes)");
            System.err.println("  [base-package]  Root package to analyze (auto-detected if omitted)");
            System.exit(1);
        }

        String classesDir = args[0];
        if (!Files.isDirectory(Paths.get(classesDir))) {
            System.err.println("ERROR: Not a directory: " + classesDir);
            System.exit(1);
        }

        JavaClasses classes;
        try {
            classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPath(classesDir);
        } catch (Exception e) {
            System.err.println("ERROR importing classes: " + e.getMessage());
            System.exit(1);
            return;
        }

        int classCount = 0;
        for (JavaClass ignored : classes) classCount++;
        if (classCount == 0) {
            System.out.println("No classes found in " + classesDir);
            return;
        }

        String basePackage = args.length > 1 ? args[1] : detectBasePackage(classes);

        System.out.println("=== ArchUnit Architecture Analysis ===");
        System.out.println("Classes dir:   " + classesDir);
        System.out.println("Base package:  " + basePackage);
        System.out.println("Total classes: " + classCount);
        System.out.println();

        Set<JavaPackage> subPackages = Collections.emptySet();
        try {
            subPackages = classes.getPackage(basePackage).getSubpackages();
        } catch (Exception e) {
            System.out.println("WARNING: Cannot resolve sub-packages for '" + basePackage + "': " + e.getMessage());
        }

        if (!subPackages.isEmpty()) {
            martinMetrics(subPackages, basePackage);
            lakosMetrics(subPackages);
        } else {
            System.out.println("--- Metrics skipped: no sub-packages under " + basePackage + " ---");
            System.out.println();
        }

        cycleAnalysis(classes, basePackage);
        layerAnalysis(classes);
        dependencySummary(classes, subPackages, basePackage);
    }

    // ---- Robert C. Martin Metrics (Ca, Ce, I, A, D) ----

    static void martinMetrics(Set<JavaPackage> packages, String basePackage) {
        System.out.println("--- Robert C. Martin Metrics ---");
        try {
            MetricsComponents<JavaClass> components = MetricsComponents.fromPackages(packages);
            ComponentDependencyMetrics m = ArchitectureMetrics.componentDependencyMetrics(components);

            System.out.printf("%-45s  %4s  %4s  %7s  %7s  %7s%n", "Component", "Ca", "Ce", "I", "A", "D");
            System.out.println("-".repeat(82));

            List<JavaPackage> sorted = packages.stream()
                .sorted(Comparator.comparing(JavaPackage::getName))
                .collect(Collectors.toList());

            List<String> zoneOfPain = new ArrayList<>();
            List<String> zoneOfUselessness = new ArrayList<>();

            for (JavaPackage pkg : sorted) {
                String name = pkg.getName();
                String shortName = name.startsWith(basePackage + ".")
                    ? name.substring(basePackage.length() + 1) : name;
                try {
                    int ca = m.getAfferentCoupling(name);
                    int ce = m.getEfferentCoupling(name);
                    double i = m.getInstability(name);
                    double a = m.getAbstractness(name);
                    double d = m.getNormalizedDistanceFromMainSequence(name);

                    String flag = "";
                    if (i < 0.3 && a < 0.3 && d > 0.4) {
                        flag = " << ZONE OF PAIN";
                        zoneOfPain.add(shortName);
                    } else if (i > 0.7 && a > 0.7 && d > 0.4) {
                        flag = " << ZONE OF USELESSNESS";
                        zoneOfUselessness.add(shortName);
                    }

                    System.out.printf("%-45s  %4d  %4d  %5.3f    %5.3f    %5.3f%s%n",
                        shortName, ca, ce, i, a, d, flag);
                } catch (Exception e) {
                    System.out.printf("%-45s  ERROR: %s%n", shortName, e.getMessage());
                }
            }

            if (!zoneOfPain.isEmpty()) {
                System.out.println();
                System.out.println("WARNING Zone of Pain (stable + concrete = hard to change): "
                    + String.join(", ", zoneOfPain));
            }
            if (!zoneOfUselessness.isEmpty()) {
                System.out.println("WARNING Zone of Uselessness (unstable + abstract): "
                    + String.join(", ", zoneOfUselessness));
            }
        } catch (Exception e) {
            System.out.println("ERROR: " + e.getMessage());
        }
        System.out.println();
    }

    // ---- Lakos Metrics (system-wide coupling) ----

    static void lakosMetrics(Set<JavaPackage> packages) {
        System.out.println("--- Lakos Metrics (system-wide coupling) ---");
        try {
            MetricsComponents<JavaClass> components = MetricsComponents.fromPackages(packages);
            LakosMetrics m = ArchitectureMetrics.lakosMetrics(components);

            System.out.printf("CCD  (Cumulative Component Dependency):  %,.0f%n", m.getCumulativeComponentDependency());
            System.out.printf("ACD  (Average Component Dependency):     %.2f%n", m.getAverageComponentDependency());
            System.out.printf("RACD (Relative ACD, 0-1):                %.4f%n", m.getRelativeAverageComponentDependency());
            System.out.printf("NCCD (Normalized CCD):                   %.4f%n", m.getNormalizedCumulativeComponentDependency());
            System.out.println();

            double nccd = m.getNormalizedCumulativeComponentDependency();
            if (nccd > 1.0) {
                System.out.println("=> NCCD > 1.0: Higher coupling than a balanced tree. Consider decoupling.");
            } else {
                System.out.println("=> NCCD <= 1.0: Coupling is within healthy range.");
            }
        } catch (Exception e) {
            System.out.println("ERROR: " + e.getMessage());
        }
        System.out.println();
    }

    // ---- Cycle Detection ----

    static void cycleAnalysis(JavaClasses classes, String basePackage) {
        System.out.println("--- Package Cycle Detection ---");
        try {
            String pattern = basePackage + ".(*)..";
            ArchRule rule = slices().matching(pattern).should().beFreeOfCycles();
            EvaluationResult result = rule.evaluate(classes);

            if (result.hasViolation()) {
                System.out.println("CYCLES DETECTED:");
                System.out.println(result.getFailureReport().toString());
            } else {
                System.out.println("No package cycles detected.");
            }
        } catch (Exception e) {
            System.out.println("ERROR: " + e.getMessage());
        }
        System.out.println();
    }

    // ---- Layer Violation Analysis ----

    static void layerAnalysis(JavaClasses classes) {
        System.out.println("--- Layer Violation Analysis ---");

        boolean hasPres = hasClassesIn(classes, "controller", "rest", "web");
        boolean hasSvc  = hasClassesIn(classes, "service", "usecase");
        boolean hasPers = hasClassesIn(classes, "repository", "dao", "persistence");

        System.out.println("Detected layers: "
            + (hasPres ? "[Presentation] " : "")
            + (hasSvc  ? "[Service] " : "")
            + (hasPers ? "[Persistence] " : "")
            + (!hasPres && !hasSvc && !hasPers ? "none" : ""));

        if (hasPres && hasSvc && hasPers) {
            try {
                EvaluationResult result = layeredArchitecture()
                    .consideringAllDependencies()
                    .layer("Presentation").definedBy("..controller..", "..rest..", "..web..")
                    .layer("Service").definedBy("..service..", "..usecase..")
                    .layer("Persistence").definedBy("..repository..", "..dao..", "..persistence..")
                    .whereLayer("Presentation").mayNotBeAccessedByAnyLayer()
                    .whereLayer("Service").mayOnlyBeAccessedByLayers("Presentation")
                    .whereLayer("Persistence").mayOnlyBeAccessedByLayers("Service")
                    .evaluate(classes);

                if (result.hasViolation()) {
                    System.out.println("VIOLATIONS:");
                    System.out.println(result.getFailureReport().toString());
                } else {
                    System.out.println("No layer violations found.");
                }
            } catch (Exception e) {
                System.out.println("ERROR: " + e.getMessage());
            }
        } else if (hasPres || hasSvc || hasPers) {
            System.out.println("Incomplete layer structure — full check requires Presentation + Service + Persistence.");
        }
        System.out.println();
    }

    // ---- Module Dependency Graph ----

    static void dependencySummary(JavaClasses allClasses, Set<JavaPackage> packages, String basePackage) {
        System.out.println("--- Module Dependency Graph ---");
        if (packages.isEmpty()) {
            System.out.println("(no sub-packages to analyze)");
            System.out.println();
            return;
        }

        List<JavaPackage> sorted = packages.stream()
            .sorted(Comparator.comparing(JavaPackage::getName))
            .collect(Collectors.toList());

        for (JavaPackage pkg : sorted) {
            String pkgName = pkg.getName();
            String shortName = pkgName.startsWith(basePackage + ".")
                ? pkgName.substring(basePackage.length() + 1) : pkgName;

            Set<String> deps = new TreeSet<>();
            for (JavaClass clazz : allClasses) {
                if (!clazz.getPackageName().startsWith(pkgName)) continue;
                for (Dependency dep : clazz.getDirectDependenciesFromSelf()) {
                    String targetPkg = dep.getTargetClass().getPackageName();
                    if (!targetPkg.startsWith(basePackage + ".") || targetPkg.startsWith(pkgName)) continue;
                    String relative = targetPkg.substring(basePackage.length() + 1);
                    String topLevel = relative.contains(".") ? relative.substring(0, relative.indexOf('.')) : relative;
                    deps.add(topLevel);
                }
            }

            System.out.println("  " + shortName + " -> " + (deps.isEmpty() ? "(none)" : String.join(", ", deps)));
        }
        System.out.println();
    }

    // ---- Helpers ----

    static String detectBasePackage(JavaClasses classes) {
        Set<String> pkgs = new TreeSet<>();
        for (JavaClass jc : classes) pkgs.add(jc.getPackageName());
        if (pkgs.isEmpty()) return "";

        String[] first = pkgs.iterator().next().split("\\.");
        String prefix = first[0];
        for (int i = 1; i < first.length; i++) {
            String candidate = prefix + "." + first[i];
            String cp = candidate + ".";
            boolean allMatch = true;
            for (String p : pkgs) {
                if (!p.equals(candidate) && !p.startsWith(cp)) { allMatch = false; break; }
            }
            if (allMatch) prefix = candidate;
            else break;
        }
        return prefix;
    }

    static boolean hasClassesIn(JavaClasses classes, String... keywords) {
        for (JavaClass c : classes) {
            String pkg = c.getPackageName();
            for (String kw : keywords) {
                if (pkg.contains("." + kw + ".") || pkg.endsWith("." + kw)) return true;
            }
        }
        return false;
    }
}
