package ai.datasqrl.schema.input;

import ai.datasqrl.parse.tree.name.Name;
import ai.datasqrl.parse.tree.name.NamePath;
import ai.datasqrl.parse.tree.name.ReservedName;
import ai.datasqrl.schema.Column;
import ai.datasqrl.schema.type.SqrlTypeVisitor;
import ai.datasqrl.schema.type.basic.BasicType;
import ai.datasqrl.schema.type.basic.DateTimeType;
import ai.datasqrl.schema.type.basic.IntegerType;
import ai.datasqrl.schema.type.basic.UuidType;
import lombok.Value;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;

public abstract class AbstractFlexibleTableConverterVisitor<T> implements FlexibleTableConverter.Visitor<T> {

    public final Deque<TableBuilder<T>> stack = new ArrayDeque<>();

    @Override
    public void beginTable(Name name, NamePath namePath, boolean isNested, boolean isSingleton, boolean hasSourceTimestamp) {
        stack.addFirst(new TableBuilder<>(name, namePath));
        augmentTable(isNested, isSingleton, hasSourceTimestamp);
    }

    protected void augmentTable(boolean isNested, boolean isSingleton, boolean hasSourceTimestamp) {
        if (!isNested) {
            addField(ReservedName.UUID,UuidType.INSTANCE,true);
            addField(ReservedName.INGEST_TIME,DateTimeType.INSTANCE,true);
            if (hasSourceTimestamp) {
                addField(ReservedName.SOURCE_TIME, DateTimeType.INSTANCE, true);
            }
        }
        if (isNested && !isSingleton) {
            addField(ReservedName.ARRAY_IDX, IntegerType.INSTANCE, true);
        }
    }

    @Override
    public Optional<T> endTable(Name name, NamePath namePath, boolean isNested, boolean isSingleton) {
        return createTable(stack.removeFirst());
    }

    protected abstract Optional<T> createTable(TableBuilder<T> tblBuilder);

    @Override
    public void addField(Name name, T type, boolean notnull) {
        stack.getFirst().add(name,nullable(type,notnull));
    }

    public abstract T nullable(T type, boolean notnull);

    public T convertBasicType(BasicType type) {
        return type.accept(getTypeConverter(),null);
    }

    protected abstract SqrlTypeConverter<T> getTypeConverter();

    protected interface SqrlTypeConverter<T> extends SqrlTypeVisitor<T,Void> {

    }

    @Value
    protected static class TableBuilder<T> {

        final Name name;
        final NamePath namePath;
        final List<Pair<Name,T>> columns = new ArrayList<>();

        void add(final Name name, T type) {
            //A name may clash with a previously added reserved name in which case we have to suffix it
            Name columnId = Column.getId(name,0);
            if (columns.stream().map(Pair::getKey).anyMatch(n -> n.equals(name))) {
                columnId = Column.getId(name,1);
            }
            columns.add(Pair.of(columnId,type));
        }

    }

}