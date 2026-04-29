package com.jsjjlt.sqlparser.entity;


import com.jsjjlt.sqlparser.utils.StringUtils;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Objects;
import java.util.Set;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RefTab {
    private String prefix;
    private String name;
    private Set<String> alias;

    public RefTab(String name) {
        if (name.contains("\\.")) {
            this.name = StringUtils.handleQuoter(name.split("\\.")[1]);
            this.prefix = StringUtils.handleQuoter(name.split("\\.")[0]);
        } else {
            this.name = StringUtils.handleQuoter(name);
            this.prefix = null;
        }
        this.alias = null;
    }

    @Override
    public String toString() {
        String tab = (prefix == null || prefix.isEmpty()) ? String.valueOf(name) : prefix + "." + name;
        return tab + (alias == null || alias.isEmpty() ? "" : ":" + alias);
    }

    public void setPrefix(String prefix) {
        this.prefix = StringUtils.handleQuoter(prefix);
    }

    public void setName(String name) {
        this.name = StringUtils.handleQuoter(name);
    }

    public void addAlias(String alias) {
        // 实现省略（与RefCol.addAlias逻辑类似）
    }

    public void addAlias(Set<String> alias) {
        // 实现省略（与RefCol.addAlias逻辑类似）
    }

    public String normalizeTableKey(String preifx) {
        String table = name == null ? "" : name.trim();
        if (table.isEmpty()) {
            return "";
        }
        String dbName = (this.prefix == null ? preifx : this.prefix) == null ? "" : preifx.trim();
        return dbName.isEmpty() ? table : dbName + "." + table;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RefTab refTab = (RefTab) o;
        return Objects.equals(prefix, refTab.prefix) &&
                Objects.equals(name, refTab.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(prefix, name);
    }
}