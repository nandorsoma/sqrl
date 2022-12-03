package com.datasqrl.config.error;

public interface ErrorHandler<E extends Exception> {

  ErrorMessage handle(E e, ErrorEmitter emitter);

  Class getHandleClass();
}