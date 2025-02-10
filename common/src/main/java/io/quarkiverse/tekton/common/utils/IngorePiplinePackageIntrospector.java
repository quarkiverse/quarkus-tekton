package io.quarkiverse.tekton.common.utils;

import com.fasterxml.jackson.databind.introspect.AnnotatedClass;
import com.fasterxml.jackson.databind.introspect.AnnotatedMember;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;

public class IngorePiplinePackageIntrospector extends JacksonAnnotationIntrospector {

    @Override
    public Boolean isIgnorableType(AnnotatedClass ac) {
        Class<?> clazz = ac.getRawType();
        Package classPackage = clazz.getPackage();

        // For example, ignore classes in the 'com.example.ignoreme' package:
        if (classPackage != null && classPackage.getName().startsWith("io.fabric8.tekton")) {
            System.out.println("Ignoring class: " + clazz.getName());
            return true;
        }

        // Fallback to default behavior:
        return super.isIgnorableType(ac);
    }

    @Override
    public boolean hasIgnoreMarker(AnnotatedMember m) {
        Class<?> clazz = m.getRawType();
        Package classPackage = clazz.getPackage();

        // For example, ignore classes in the 'com.example.ignoreme' package:
        if (classPackage != null && classPackage.getName().startsWith("io.fabric8.tekton")) {
            System.out.println("Ignoring class: " + clazz.getName());
            return true;
        }

        // Fallback to default behavior:
        return super.hasIgnoreMarker(m);
    }

}
