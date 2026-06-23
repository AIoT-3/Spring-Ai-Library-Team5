package com.nhnacademy.library.core.config;

import org.hibernate.boot.model.FunctionContributions;
import org.hibernate.boot.model.FunctionContributor;
import org.hibernate.type.StandardBasicTypes;

public class PostgreSQLFunctionContributor implements FunctionContributor {
    @Override
    public void contributeFunctions(FunctionContributions functionContributions) {
        functionContributions.getFunctionRegistry()
                .registerPattern(
                        "ts_match_korean",
                        "to_tsvector('korean', ?1) @@ plainto_tsquery('korean', ?2)",
                        functionContributions.getTypeConfiguration()
                                .getBasicTypeRegistry()
                                .resolve(StandardBasicTypes.BOOLEAN)
                );
    }
}
