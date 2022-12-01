package ai.datasqrl.config;


import ai.datasqrl.config.error.ErrorCollector;
import ai.datasqrl.config.util.ConfigurationUtil;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.experimental.SuperBuilder;

import javax.validation.Valid;

@SuperBuilder
@NoArgsConstructor
@Getter
public class GlobalCompilerConfiguration extends GlobalEngineConfiguration {

    @Builder.Default @NonNull @Valid
    CompilerConfiguration compiler = new CompilerConfiguration();

    public CompilerConfiguration initializeCompiler(ErrorCollector errors) {
        if (!ConfigurationUtil.javaxValidate(this,errors)) return null;
        return compiler;
    }

}