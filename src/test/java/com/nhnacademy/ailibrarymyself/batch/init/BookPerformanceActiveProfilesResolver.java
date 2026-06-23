package com.nhnacademy.ailibrarymyself.batch.init;

import org.springframework.test.context.ActiveProfilesResolver;

final class BookPerformanceActiveProfilesResolver implements ActiveProfilesResolver {

    private static final String PROFILE_PROPERTY = "book.performance.profile";

    @Override
    public String[] resolve(Class<?> testClass) {
        return new String[]{System.getProperty(PROFILE_PROPERTY, "test")};
    }
}
