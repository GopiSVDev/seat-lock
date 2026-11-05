package com.seatlock.backend;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

// Module boundaries, checked on every build instead of by convention
@AnalyzeClasses(packages = "com.seatlock.backend", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static final String BASE = "com.seatlock.backend.";

    @ArchTest
    static final ArchRule catalogueInternalsStayInside = internalsOf("catalogue");

    @ArchTest
    static final ArchRule inventoryInternalsStayInside = internalsOf("inventory");

    @ArchTest
    static final ArchRule bookingInternalsStayInside = internalsOf("booking");

    @ArchTest
    static final ArchRule catalogueDependsOnNoOtherModule = noClasses()
            .that().resideInAPackage(BASE + "catalogue..")
            .should().dependOnClassesThat().resideInAnyPackage(BASE + "inventory..", BASE + "booking..");

    @ArchTest
    static final ArchRule nothingDependsOnBooking = noClasses()
            .that().resideOutsideOfPackage(BASE + "booking..")
            .should().dependOnClassesThat().resideInAPackage(BASE + "booking..");

    @ArchTest
    static final ArchRule sharedDependsOnNoModule = noClasses()
            .that().resideInAPackage(BASE + "shared..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(BASE + "catalogue..", BASE + "inventory..", BASE + "booking..");

    private static ArchRule internalsOf(String module) {
        return noClasses()
                .that().resideOutsideOfPackage(BASE + module + "..")
                .should().dependOnClassesThat().resideInAPackage(BASE + module + ".internal..")
                .as(module + ".internal is used only inside " + module);
    }
}
