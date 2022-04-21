package ai.datasqrl.io.impl.file;

import ai.datasqrl.config.error.ErrorCollector;
import ai.datasqrl.io.sinks.DataSinkImplementation;
import com.google.common.base.Strings;
import java.io.IOException;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;

@Builder
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class DirectorySinkImplementation implements DataSinkImplementation {

  public static final String DEFAULT_PART_DELIMITER = "_p";

  @NonNull @NotNull @Size(min = 3)
  String uri;
  @Builder.Default
  @NonNull @NotNull @Size(min = 1)
  String partDelimiter = DEFAULT_PART_DELIMITER;


  @Override
  public boolean initialize(ErrorCollector errors) {
    FilePath directoryPath = new FilePath(uri);
    try {
      FilePath.Status status = directoryPath.getStatus();
      if (!status.exists() || !status.isDir()) {
        errors.fatal("URI [%s] is not a directory", uri);
        return false;
      }
    } catch (IOException e) {
      errors.fatal("URI [%s] is invalid: %s", uri, e);
      return false;
    }

    if (Strings.isNullOrEmpty(partDelimiter)) {
      errors.fatal("Part delimiter can not be empty");
    }
    return true;
  }
}
