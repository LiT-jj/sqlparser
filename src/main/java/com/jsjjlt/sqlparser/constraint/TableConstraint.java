package com.jsjjlt.sqlparser.constraint;

import com.jsjjlt.sqlparser.entity.RefCol;
import com.jsjjlt.sqlparser.entity.RefTab;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

@Data
@NoArgsConstructor
public class TableConstraint {
    private RefTab table;
    private Map<RefCol, ColumnConstraint> column2constraint;

    public TableConstraint(RefTab refTab) {
        this.table = refTab;
        column2constraint = new HashMap<>();
    }

    public void addConstraint(ColumnConstraint constraint) {
        RefCol refCol = constraint.getRefCol();
        ColumnConstraint columnConstraint = column2constraint.getOrDefault(refCol, new ColumnConstraint(refCol));
        columnConstraint.merge(constraint);
        column2constraint.put(refCol, columnConstraint);
    }
}