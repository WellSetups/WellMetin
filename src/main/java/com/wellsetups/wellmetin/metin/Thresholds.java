package com.wellsetups.wellmetin.metin;

import com.wellsetups.wellmetin.util.Checks;
import java.util.Comparator;
import java.util.List;

public final class Thresholds {
    public interface Threshold {
        String id();
        double percent();
    }

    private Thresholds() { }

    public static <T extends Threshold> List<T> crossed(List<T> values, double before, double after) {
        Checks.range("before", before, 0, 100);
        Checks.range("after", after, 0, 100);
        return values.stream().filter(value -> (before > value.percent() && after <= value.percent())
                        || (value.percent() == 100 && before == 100 && after < 100))
                .sorted(Comparator.comparingDouble(Threshold::percent).reversed()).toList();
    }
}
