//DEPS org.bsc.langgraph4j:langgraph4j-javelit:1.9-SNAPSHOT

import io.javelit.core.Jt;
import org.bsc.javelit.JtDataTable;
import org.bsc.javelit.JtMultiSelect;

import java.util.List;
import java.util.Objects;
import java.util.Set;

public class JtDataTableApp {


    public static void main(String[] args) {

        var app = new JtDataTableApp();

        app.view();
    }

    record DataItem(String name, int age, String address) {
    }

    public void view() {
        Jt.title("JtDataTable test App").use();

        //final var disabled = Jt.toggle("disable").value(false).use();

        var data = List.of(
                new DataItem("one", 10, "address1"),
                new DataItem("two", 20, "address2"),
                new DataItem("three", 30, "address3"),
                new DataItem("four", 40, "address4"),
                new DataItem("five", 50, "address5"));

        var result = JtDataTable.builder(data)
                .column("Name", DataItem::name)
                .column("Age", ( v ) -> Objects.toString(v.age()))
                .column("Address", DataItem::address)
                .use();

        Jt.markdown( Objects.toString(result) ).use();

    }
}
