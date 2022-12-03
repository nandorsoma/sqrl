package com.datasqrl.config.error;

import com.datasqrl.config.error.ErrorLocation.FileLocation;
import com.datasqrl.config.error.ErrorMessage.Implementation;
import com.datasqrl.config.error.ErrorMessage.Severity;
import com.datasqrl.parse.SqrlAstException;
import java.util.Optional;

public class SqrlAstExceptionHandler implements ErrorHandler<SqrlAstException> {

  @Override
  public ErrorMessage handle(SqrlAstException e, ErrorEmitter emitter) {
    return new Implementation(Optional.ofNullable(e.getErrorCode()), e.getMessage(),
        emitter.getBaseLocation()
            .atFile(new FileLocation(e.getPos().getLineNum(), e.getPos().getColumnNum())),
        Severity.FATAL,
        emitter.getSourceMap());
  }

  @Override
  public Class getHandleClass() {
    return SqrlAstException.class;
  }
}