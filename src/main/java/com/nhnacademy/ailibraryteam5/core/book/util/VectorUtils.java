package com.nhnacademy.ailibraryteam5.core.book.util;

import java.util.List;

public class VectorUtils {

    public static float[] AverageVector(List<float[]> vectors) {
        if (vectors.isEmpty()) {
            throw new IllegalArgumentException("벡터 리스트가 비어 있습니다.");
        }

        int dimensions = vectors.get(0).length;
        float[] avgVector = new float[dimensions];

        // 각 차원별로 합계 계산
        for (float[] vector : vectors) {
            for (int i = 0; i < dimensions; i++) {
                avgVector[i] += vector[i];
            }
        }

        // 평균 계산 (합계 / 개수)
        for (int i = 0; i < dimensions; i++) {
            avgVector[i] /= vectors.size();
        }

        return avgVector;
    }

    public static double cosineSimilarity(float[] v1, float[] v2) {
        if (v1.length != v2.length) {
            throw new IllegalArgumentException(
                    "벡터 길이가 같아야 합니다: v1=" + v1.length + ", v2=" + v2.length
            );
        }

        double dotProduct = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;
        for (int i = 0; i < v1.length; i++) {
            dotProduct += v1[i] * v2[i];
            norm1 += v1[i] * v1[i];
            norm2 += v2[i] * v2[i];
        }

        if (norm1 == 0.0 || norm2 == 0.0) {
            return 0.0;
        }

        return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }
}
