package com.dpe.common.compile;

import com.dpe.common.model.ResourceLocation;

import java.util.List;
import java.util.Map;

/**
 * 编译结果，不可变。
 * mcfunctions: ResourceLocation -> 函数文件内容（含命令行）。
 * jsonFiles: ResourceLocation -> JSON 文件内容（tag/advancement/loot_table）。
 * errors: 校验错误列表（success=false 时非空）。
 */
public record CompileResult(boolean success,
                            Map<ResourceLocation, String> mcfunctions,
                            Map<ResourceLocation, String> jsonFiles,
                            List<ValidationError> errors) {

    public CompileResult {
        mcfunctions = mcfunctions == null ? Map.of() : Map.copyOf(mcfunctions);
        jsonFiles = jsonFiles == null ? Map.of() : Map.copyOf(jsonFiles);
        errors = errors == null ? List.of() : List.copyOf(errors);
    }

    /** 失败结果工厂。 */
    public static CompileResult failure(List<ValidationError> errors) {
        return new CompileResult(false, Map.of(), Map.of(), errors);
    }
}
