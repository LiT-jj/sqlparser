package com.jsjjlt.sqlparser.entity;

import com.jsjjlt.sqlparser.utils.StringUtils;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RefCol {
    private RefTab prefix;
    private String name;
    private Set<String> alias;

    public RefCol(String name){
        this.prefix = null;
        this.name = StringUtils.handleQuoter(name);
        this.alias = null;
    }

    public RefCol(RefTab refTab, String name){
        this.prefix = refTab;
        this.name = StringUtils.handleQuoter(name);
        this.alias = null;
    }

    public void setPrefix(String prefix) {
        String tableName = null;
        String dbName = null;
        if (prefix.contains("\\.")){
            dbName = prefix.split("\\.")[0];
            tableName = prefix.split("\\.")[1];
        } else {
            tableName = prefix;
        }
        this.prefix = new RefTab(dbName, tableName);
    }

    public void setPrefix(RefTab prefix) {
        this.prefix = prefix;
    }

    @Override
    public String toString() {
        String col = (prefix == null ? "" : prefix + ".") + name;
        return col + (alias == null || alias.isEmpty() ? "" : ":" + alias);
    }

    public void addAlias(String alias){
        if (this.alias == null){
            this.alias = new HashSet<>();
        }
        if (alias != null){
            this.alias.add(StringUtils.handleQuoter(alias));
        }
    }

    public void addAlias(Set<String> alias){
        if (this.alias == null){
            this.alias = new HashSet<>();
        }
        if (alias != null){
            for (String alia : alias){
                addAlias(alia);
            }
        }
    }

    public void setName(String name) {
        this.name = StringUtils.handleQuoter(name);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RefCol refCol = (RefCol) o;
        return Objects.equals(prefix, refCol.prefix) &&
                Objects.equals(name, refCol.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(prefix, name);
    }
}