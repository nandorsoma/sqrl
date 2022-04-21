package ai.datasqrl.io;

import ai.datasqrl.config.error.ErrorCollector;
import ai.datasqrl.io.formats.FormatConfiguration;
import ai.datasqrl.io.impl.CanonicalizerConfiguration;
import ai.datasqrl.parse.tree.name.NameCanonicalizer;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.io.Serializable;
import java.nio.charset.Charset;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@SuperBuilder
@ToString
public abstract class SharedConfiguration implements Serializable {

  public static final String DEFAULT_CHARSET = "UTF-8";

  @Builder.Default
  @NonNull
  @NotNull
  @Valid
  protected CanonicalizerConfiguration canonicalizer = CanonicalizerConfiguration.system;
  @Builder.Default
  @NonNull
  @NotNull
  protected String charset = DEFAULT_CHARSET;
  @Valid
  protected FormatConfiguration format;


  @JsonIgnore
  public NameCanonicalizer getNameCanonicalizer() {
    return canonicalizer.getCanonicalizer();
  }

  @JsonIgnore
  protected abstract boolean formatRequired();

  @JsonIgnore
  public Charset getCharsetObject() {
    return Charset.forName(charset);
  }


  public boolean initialize(ErrorCollector errors) {
    try {
      Charset cs = Charset.forName(charset);
    } catch (Exception e) {
      errors.fatal("Unsupported charset: %s", charset);
      return false;
    }
    if (format == null) {
      if (formatRequired()) {
        errors.fatal("Need to configure a format");
        return false;
      }
      return true;
    } else {
      return format.initialize(null, errors.resolve("format"));
    }
  }

}
