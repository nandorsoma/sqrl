package com.datasqrl.functions.string;

import static com.datasqrl.NamespaceObjectUtil.createFunctionFromFlink;
import static com.datasqrl.NamespaceObjectUtil.createFunctionFromStdOpTable;

import com.datasqrl.canonicalizer.NamePath;
import com.datasqrl.function.AbstractFunctionModule;
import com.datasqrl.function.StdLibrary;
import com.datasqrl.module.NamespaceObject;
import com.google.auto.service.AutoService;
import java.util.List;

@AutoService(StdLibrary.class)
public class StdStringLibraryImpl extends AbstractFunctionModule implements StdLibrary {

  private static final NamePath LIB_NAME = NamePath.of("string");
  private static List<NamespaceObject> stringFunctions = java.util.List.of(
      createFunctionFromFlink("length", "CHAR_LENGTH"),
      createFunctionFromFlink("upper", "UPPER"),
      createFunctionFromFlink("lower", "LOWER"),
      createFunctionFromFlink("position", "POSITION"),
      createFunctionFromStdOpTable("trim"), //flink's trim fnc has addl args
      createFunctionFromFlink("ltrim", "LTRIM"),
      createFunctionFromFlink("rtrim", "RTRIM"),
      createFunctionFromFlink("repeat", "REPEAT"),
      createFunctionFromFlink("regexReplace", "REGEXP_REPLACE"),
      createFunctionFromFlink("overlay", "OVERLAY"),
      createFunctionFromFlink("substring", "SUBSTRING"),
      createFunctionFromFlink("replace", "REPLACE"),
      createFunctionFromFlink("regexExtract", "REGEXP_EXTRACT"),
      createFunctionFromFlink("initcap", "INIT_CAP"),
      createFunctionFromFlink("concat", "CONCAT"),
      createFunctionFromFlink("concatWS", "CONCAT_WS"),
      createFunctionFromFlink("lpad", "LPAD"),
      createFunctionFromFlink("rpad", "RPAD"),
      createFunctionFromFlink("fromBase64", "FROM_BASE64"),
      createFunctionFromFlink("toBase64", "TO_BASE64"),
      createFunctionFromFlink("ascii", "ASCII"),
      createFunctionFromFlink("chr", "CHR"),
      createFunctionFromFlink("decode", "DECODE"),
      createFunctionFromFlink("encode", "ENCODE"),
      createFunctionFromFlink("instr", "INSTR"),
      createFunctionFromFlink("left", "LEFT"),
      createFunctionFromFlink("right", "RIGHT"),
      createFunctionFromFlink("locate", "LOCATE"),
      createFunctionFromFlink("parseURL", "PARSE_URL"),
      createFunctionFromFlink("regexp", "REGEXP"),
      createFunctionFromFlink("reverse", "REVERSE"),
      createFunctionFromFlink("splitIndex", "SPLIT_INDEX"),
      createFunctionFromFlink("strToMap", "STR_TO_MAP"),
      createFunctionFromFlink("substr", "SUBSTR")
  );

  public StdStringLibraryImpl() {
    super(stringFunctions);
  }

  public NamePath getPath() {
    return LIB_NAME;
  }
}
