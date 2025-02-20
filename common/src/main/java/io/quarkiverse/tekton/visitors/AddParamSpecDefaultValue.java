package io.quarkiverse.tekton.visitors;

import io.fabric8.kubernetes.api.builder.TypedVisitor;
import io.fabric8.tekton.v1.ParamSpecFluent;

public class AddParamSpecDefaultValue extends TypedVisitor<ParamSpecFluent> {

    private String name;
    private String defaultValue;

    public AddParamSpecDefaultValue(String name, String defaultValue) {
        this.name = name;
        this.defaultValue = defaultValue;
    }

    @Override
    public void visit(ParamSpecFluent paramSpec) {
        if (!name.equals(paramSpec.getName())) {
            return;
        }
        paramSpec.withNewDefault(defaultValue);
    }

}
