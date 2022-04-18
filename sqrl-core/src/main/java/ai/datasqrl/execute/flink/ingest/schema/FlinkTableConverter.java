package ai.datasqrl.execute.flink.ingest.schema;

import ai.datasqrl.execute.flink.environment.util.FlinkUtilities;
import ai.datasqrl.io.sources.SourceRecord;
import ai.datasqrl.parse.tree.name.Name;
import ai.datasqrl.parse.tree.name.NamePath;
import ai.datasqrl.parse.tree.name.ReservedName;
import ai.datasqrl.schema.type.ArrayType;
import ai.datasqrl.schema.type.RelationType;
import ai.datasqrl.schema.type.Type;
import ai.datasqrl.schema.type.basic.BasicType;
import ai.datasqrl.schema.type.basic.BigIntegerType;
import ai.datasqrl.schema.type.basic.BooleanType;
import ai.datasqrl.schema.type.basic.DateTimeType;
import ai.datasqrl.schema.type.basic.DoubleType;
import ai.datasqrl.schema.type.basic.FloatType;
import ai.datasqrl.schema.type.basic.IntegerType;
import ai.datasqrl.schema.type.basic.IntervalType;
import ai.datasqrl.schema.type.basic.NumberType;
import ai.datasqrl.schema.type.basic.StringType;
import ai.datasqrl.schema.type.basic.UuidType;
import ai.datasqrl.schema.type.constraint.Cardinality;
import ai.datasqrl.schema.type.constraint.ConstraintHelper;
import ai.datasqrl.schema.type.schema.FlexibleDatasetSchema;
import ai.datasqrl.schema.type.schema.FlexibleSchemaHelper;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.typeinfo.BasicTypeInfo;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.table.api.DataTypes;
import org.apache.flink.table.api.Schema;
import org.apache.flink.table.types.DataType;
import org.apache.flink.types.Row;
import org.apache.flink.types.RowKind;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class FlinkTableConverter {

    public Pair<Schema, TypeInformation> tableSchemaConversion(FlexibleDatasetSchema.TableField table) {
        NamePath path = NamePath.of(table.getName());
        Schema.Builder schemaBuilder = Schema.newBuilder();
        List<String> rowNames = new ArrayList<>();
        List<TypeInformation> rowCols = new ArrayList<>();
        getFields(table.getFields()).map(t -> fieldTypeSchemaConversion(t.getLeft(),t.getMiddle(),t.getRight(),path)).forEach(p -> {
            DataTypes.Field dtf = p.getLeft();
            schemaBuilder.column(dtf.getName(),dtf.getDataType());
            rowNames.add(dtf.getName());
            rowCols.add(p.getRight());
        });
        schemaBuilder.column(ReservedName.UUID.getCanonical(), toFlinkDataType(UuidType.INSTANCE).notNull());
        rowNames.add(ReservedName.UUID.getCanonical()); rowCols.add(FlinkUtilities.getFlinkTypeInfo(UuidType.INSTANCE,false));
        schemaBuilder.column(ReservedName.INGEST_TIME.getCanonical(), DataTypes.TIMESTAMP_LTZ(3).notNull());
        rowNames.add(ReservedName.INGEST_TIME.getCanonical()); rowCols.add(FlinkUtilities.getFlinkTypeInfo(
            DateTimeType.INSTANCE,false));
        schemaBuilder.column(ReservedName.SOURCE_TIME.getCanonical(), DataTypes.TIMESTAMP_LTZ(3).notNull());
        rowNames.add(ReservedName.SOURCE_TIME.getCanonical()); rowCols.add(FlinkUtilities.getFlinkTypeInfo(DateTimeType.INSTANCE,false));
        //TODO: adjust based on configuration
//        schemaBuilder.columnByExpression("__rowtime", "CAST(_ingest_time AS TIMESTAMP_LTZ(3))");
        schemaBuilder.watermark(ReservedName.INGEST_TIME.getCanonical(), ReservedName.INGEST_TIME.getCanonical() + " - INTERVAL '10' SECOND");
        return Pair.of(schemaBuilder.build(),Types.ROW_NAMED(rowNames.toArray(new String[0]),rowCols.toArray(new TypeInformation[0])));
    }

    private static Stream<Triple<Name, FlexibleDatasetSchema.FieldType, Boolean>> getFields(RelationType<FlexibleDatasetSchema.FlexibleField> relation) {
        return relation.getFields().stream().flatMap(field -> field.getTypes().stream().map( ftype -> {
            Name name = FlexibleSchemaHelper.getCombinedName(field,ftype);
            boolean isMixedType = field.getTypes().size()>1;
            return Triple.of(name, ftype, isMixedType);
        }));
    }

    private Pair<DataTypes.Field,TypeInformation> fieldTypeSchemaConversion(Name name, FlexibleDatasetSchema.FieldType ftype,
                                                      boolean isMixedType, NamePath path) {
        boolean notnull = !isMixedType && ConstraintHelper.isNonNull(ftype.getConstraints());

        DataType dt; TypeInformation ti;
        if (ftype.getType() instanceof RelationType) {
            List<DataTypes.Field> dtfs = new ArrayList<>();
            List<TypeInformation> tis = new ArrayList<>();
            final NamePath nestedpath = path.concat(name);
            getFields((RelationType<FlexibleDatasetSchema.FlexibleField>) ftype.getType())
                    .map(t -> fieldTypeSchemaConversion(t.getLeft(),t.getMiddle(),t.getRight(),nestedpath))
                    .forEach(p -> {
                        dtfs.add(p.getLeft());
                        tis.add(p.getRight());
                    });
            if (!isSingleton(ftype)) {
                dtfs.add(DataTypes.FIELD(ReservedName.ARRAY_IDX.getCanonical(),toFlinkDataType(
                    IntegerType.INSTANCE).notNull()));
                tis.add(BasicTypeInfo.LONG_TYPE_INFO);
            }
            dt = DataTypes.ROW(dtfs.toArray(new DataTypes.Field[dtfs.size()]));
            ti = Types.ROW_NAMED(dtfs.stream().map(dtf -> dtf.getName()).toArray(i -> new String[i]),
                    tis.toArray(new TypeInformation[tis.size()]));
            if (!isSingleton(ftype)) {
                dt = DataTypes.ARRAY(dt.notNull());
                ti = Types.OBJECT_ARRAY(ti);
            }
            notnull = !isMixedType && !hasZeroOneMultiplicity(ftype);
        } else if (ftype.getType() instanceof ArrayType) {
            Pair<DataType,TypeInformation> p = wrapArraySchema((ArrayType) ftype.getType());
            dt = p.getLeft(); ti = p.getRight();
        } else {
            assert ftype.getType() instanceof BasicType;
            dt = toFlinkDataType((BasicType)ftype.getType());
            ti = FlinkUtilities.getFlinkTypeInfo((BasicType) ftype.getType(), false);
        }
        if (notnull) dt = dt.notNull();
        else dt = dt.nullable();
        return Pair.of(DataTypes.FIELD(name.getCanonical(),dt),ti);
    }

    private Pair<DataType,TypeInformation> wrapArraySchema(ArrayType arrType) {
        Type subType = arrType.getSubType();
        DataType dt;
        TypeInformation ti;
        if (subType instanceof ArrayType) {
            Pair<DataType,TypeInformation> p = wrapArraySchema((ArrayType) subType);
            dt = p.getLeft(); ti = p.getRight();
        } else {
            assert subType instanceof BasicType;
            dt = toFlinkDataType((BasicType)subType);
            ti = FlinkUtilities.getFlinkTypeInfo((BasicType) subType, false);
        }
        return Pair.of(DataTypes.ARRAY(dt), Types.OBJECT_ARRAY(ti));
    }

    private static boolean isSingleton(FlexibleDatasetSchema.FieldType ftype) {
        return ConstraintHelper.getCardinality(ftype.getConstraints()).isSingleton();
    }

    private static boolean hasZeroOneMultiplicity(FlexibleDatasetSchema.FieldType ftype) {
        Cardinality card = ConstraintHelper.getCardinality(ftype.getConstraints());
        return card.isSingleton() && card.getMin()==0;
    }



    public DataType toFlinkDataType(BasicType type) {
        if (type instanceof StringType) {
            return DataTypes.STRING();
        } else if (type instanceof DateTimeType) {
            return DataTypes.TIMESTAMP_LTZ(3);
        } else if (type instanceof BooleanType) {
            return DataTypes.BOOLEAN();
        } else if (type instanceof BigIntegerType) {
            return DataTypes.BIGINT();
        } else if (type instanceof DoubleType) {
            return DataTypes.DOUBLE();
        } else if (type instanceof FloatType) {
            return DataTypes.FLOAT();
        } else if (type instanceof IntegerType) {
            return DataTypes.BIGINT();
        } else if (type instanceof NumberType) {
            return DataTypes.DOUBLE();
        } else if (type instanceof IntervalType) {
            return DataTypes.BIGINT();
        } else if (type instanceof UuidType) {
            return DataTypes.STRING();
        } else {
            throw new UnsupportedOperationException("Unexpected data type: " + type);
        }
    }

    public SourceRecord2RowMapper getRowMapper(FlexibleDatasetSchema.TableField tableSchema) {
        return new SourceRecord2RowMapper(tableSchema);
    }

    public static class SourceRecord2RowMapper implements MapFunction<SourceRecord.Named, Row> {

        private final FlexibleDatasetSchema.TableField tableSchema;

        public SourceRecord2RowMapper(FlexibleDatasetSchema.TableField tableSchema) {
            this.tableSchema = tableSchema;
        }

        @Override
        public Row map(SourceRecord.Named sourceRecord) throws Exception {
            Object[] cols = constructRows(sourceRecord.getData(), tableSchema.getFields());
            int offset = cols.length;
            cols = Arrays.copyOf(cols,cols.length+3);
            cols[offset++]=sourceRecord.getUuid().toString();
            cols[offset++]=sourceRecord.getIngestTime();
            cols[offset++]=sourceRecord.getSourceTime();
            return Row.ofKind(RowKind.INSERT,cols);
        }

        private Object[] constructRows(Map<Name, Object> data, RelationType<FlexibleDatasetSchema.FlexibleField> schema) {
            return getFields(schema)
                    .map(t -> {
                        Name name = t.getLeft();
                        FlexibleDatasetSchema.FieldType ftype = t.getMiddle();
                        if (ftype.getType() instanceof RelationType) {
                            RelationType<FlexibleDatasetSchema.FlexibleField> subType = (RelationType<FlexibleDatasetSchema.FlexibleField>) ftype.getType();
                            if (isSingleton(ftype)) {
                                return Row.of(constructRows((Map<Name, Object>) data.get(name),subType));
                            } else {
                                int idx = 0;
                                List<Map<Name, Object>> nestedData = (List<Map<Name, Object>>)data.get(name);
                                Row[] result = new Row[nestedData.size()];
                                for (Map<Name, Object> item : nestedData) {
                                    Object[] cols = constructRows(item,subType);
                                    //Add index
                                    cols = Arrays.copyOf(cols,cols.length+1);
                                    cols[cols.length-1] = Long.valueOf(idx);
                                    result[idx]=Row.of(cols);
                                    idx++;
                                }
                                return result;
                            }
                        } else {
                            //Data is already correctly prepared by schema validation map-step
                            return data.get(name);
                        }
                    })
                    .toArray();
        }

    }


}