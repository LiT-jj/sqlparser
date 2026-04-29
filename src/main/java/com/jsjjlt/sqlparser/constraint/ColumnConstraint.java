package com.jsjjlt.sqlparser.constraint;

import com.jsjjlt.sqlparser.entity.Equal;
import com.jsjjlt.sqlparser.entity.RefCol;
import com.jsjjlt.sqlparser.entity.Relate;
import com.jsjjlt.sqlparser.range.Range;
import com.jsjjlt.sqlparser.range.StringRange;
import lombok.Data;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

@Data
public class ColumnConstraint {
    public RefCol refCol;
    public Set<Equal> equals;
    public Set<Range> ranges;
    public Set<Relate> relates;

    public ColumnConstraint(RefCol refCol) {
        this.refCol = refCol;
        this.equals = new HashSet<>();
        this.ranges = new HashSet<>();
        this.relates = new HashSet<>();
    }

    public ColumnConstraint() {
        equals = new HashSet<>();
        ranges = new HashSet<>();
        relates = new HashSet<>();
    }

    public void merge(ColumnConstraint columnConstraint) {
        if (columnConstraint.getEquals() != null) equals.addAll(columnConstraint.getEquals());
        if (columnConstraint.getRanges() != null) ranges.addAll(columnConstraint.getRanges());
        if (columnConstraint.getRelates() != null) relates.addAll(columnConstraint.getRelates());
    }

    public void addEqual(String value) {
        this.addEqual(new Equal(value));
    }

    public void addEqual(Equal element) {
        this.equals.add(element);
    }

    public void addEqual(Collection<Equal> elements) {
        for (Equal element : elements) {
            addEqual(element);
        }
    }

    public void addRange(Range element) {
        this.ranges.add(element);
    }

    public void addRange(Collection<Range> elements) {
        for (Range element : elements) {
            addRange(element);
        }
    }

    public void addRelate(Relate element) {
        this.relates.add(element);
    }

    public void addRelate(Collection<Relate> elements) {
        for (Relate element : elements) {
            addRelate(element);
        }
    }

    public void addConstraint(String operator, String ores) {
        switch (operator) {
            case "=":
                this.addEqual(new Equal(ores, false));
                break;
            case "!=":
            case "<>":
                // this.addNotEqualConstraint(ores);
                break;
            case "<":
                addRange(new StringRange(null, ores));
                break;
            case "<=":
                addRange(new StringRange(null, ores));
                this.addEqual(new Equal(ores));
                break;
            case ">":
                this.addRange(new StringRange(ores, null));
                break;
            case ">=":
                this.addRange(new StringRange(ores, null));
                this.addEqual(new Equal(ores));
                break;
        }
    }
}