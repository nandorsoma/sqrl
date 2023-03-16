package com.datasqrl.function.builtin.string;

import static com.datasqrl.function.builtin.FunctionUtil.createFunctionFromFlink;

import com.datasqrl.function.builtin.AbstractFunctionModule;
import com.datasqrl.function.builtin.time.StdLibrary;
import com.datasqrl.name.NamePath;
import com.datasqrl.plan.local.generate.NamespaceObject;
import com.google.auto.service.AutoService;
import java.util.List;

@AutoService(StdLibrary.class)
public class StdStringLibraryImpl extends AbstractFunctionModule implements StdLibrary {

  private static final NamePath LIB_NAME = NamePath.of("string");
  private static List<NamespaceObject> stringFunctions = List.of(
      createFunctionFromFlink("length", "CHAR_LENGTH"),
      createFunctionFromFlink("upper", "UPPER"),
      createFunctionFromFlink("position", "POSITION"),
      createFunctionFromFlink("trim", "TRIM"),
      createFunctionFromFlink("ltrim", "LTRIM"),
      createFunctionFromFlink("rtrim", "RTRIM"),
      createFunctionFromFlink("repeat", "REPEAT"),
      createFunctionFromFlink("regexReplace", "REGEXP_REPLACE"),
      createFunctionFromFlink("overlay", "OVERLAY"),
      createFunctionFromFlink("substring", "SUBSTRING"),
      createFunctionFromFlink("replace", "REPLACE"),
      createFunctionFromFlink("regexpExtract", "REGEXP_EXTRACT"),
      createFunctionFromFlink("initcap", "INITCAP"),
      createFunctionFromFlink("concat", "CONCAT_FUNCTION"),
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