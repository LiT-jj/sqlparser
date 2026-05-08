import com.jsjjlt.sqlparser.JsqlParser;
import com.jsjjlt.sqlparser.ParseResult;
import com.jsjjlt.sqlparser.entity.SQLContext;
import lombok.val;

public class Test {
    public static void main(String[] args) {
        val jsqlParser = new JsqlParser();
        ParseResult parse = jsqlParser.parse("select * from (select c from t1 union select c from t2 union select c from t3) as tab1 left join (select c from t4 union select c from t5) as tab2 on tab1.c = tab2.c");
        if (parse.hasErrors()) {
            parse.getErrors().forEach(System.out::println);
        }

        /* ParseResult parse = jsqlParser.parse("select * from db1.t1 where a = 1");
        if (parse.hasErrors()) {
            parse.getErrors().forEach(System.out::println);
        }

        parse = jsqlParser.parse("select * from db1.tab1 t1 left join db2.tab2 t2 on t1.a = t2.b where t1.c = 4");
        if (parse.hasErrors()) {
            parse.getErrors().forEach(System.out::println);
        }


        parse = jsqlParser.parse("select * from (select c from t1 union select c from t2 union select c from t3) as t1  where t1.c = 4");
        if (parse.hasErrors()) {
            parse.getErrors().forEach(System.out::println);
        }

        parse = jsqlParser.parse("select * from (select c from t1 union select c from t2 union select c from t3) as tab1 left join t4 on tab1.c = t4.b");
        if (parse.hasErrors()) {
            parse.getErrors().forEach(System.out::println);
        }
        parse = jsqlParser.parse("select * from (select c from t1 union select c from t2 union select c from t3) as tab1 left join (select c from t4 union select c from t5) as tab2 on tab1.c = tab2.c");
        if (parse.hasErrors()) {
            parse.getErrors().forEach(System.out::println);
        }*/
    }
}
