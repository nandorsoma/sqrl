/*
 * Copyright (c) 2021, DataSQRL. All rights reserved. Use is subject to license terms.
 */
package com.datasqrl.cmd;

import com.datasqrl.error.ErrorCollector;
import com.datasqrl.error.ErrorPrinter;
import com.datasqrl.graphql.GraphQLServer;
import com.datasqrl.graphql.server.Model.RootGraphqlModel;
import com.datasqrl.io.impl.jdbc.JdbcDataSystemConnector;
import io.vertx.core.Vertx;
import io.vertx.jdbcclient.JDBCConnectOptions;
import io.vertx.jdbcclient.JDBCPool;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.PgPool;
import io.vertx.pgclient.impl.PgPoolOptions;
import io.vertx.sqlclient.PoolOptions;
import io.vertx.sqlclient.SqlClient;
import java.util.concurrent.CompletableFuture;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;

@Slf4j
public abstract class AbstractCommand implements Runnable {

  @CommandLine.ParentCommand
  protected RootCommand root;

  @SneakyThrows
  public void run() {
    ErrorCollector collector = ErrorCollector.root();
    try {
      runCommand(collector);
      root.statusHook.onSuccess();
    } catch (Exception e) {
      collector.getCatcher().handle(e);
      e.printStackTrace();
      root.statusHook.onFailure();
    }
    System.out.println(ErrorPrinter.prettyPrint(collector));
  }

  protected abstract void runCommand(ErrorCollector errors) throws Exception;

}
