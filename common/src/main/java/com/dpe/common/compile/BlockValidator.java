package com.dpe.common.compile;

import com.dpe.common.block.BlockField;
import com.dpe.common.block.BlockFieldType;
import com.dpe.common.block.BlockSchema;
import com.dpe.common.block.BlockSchemaRegistry;
import com.dpe.common.block.EditorBlock;
import com.dpe.common.block.EditorState;
import com.dpe.common.model.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * 积木块状态校验器。检查 schema 存在、required 字段、ENUM 合法性、
 * RESOURCE_LOCATION 可解析、类型匹配、父子 category 兼容。
 */
public final class BlockValidator {

    /**
     * 校验整个编辑器状态。
     * @return 错误列表（空表示通过）
     */
    public List<ValidationError> validate(EditorState state, BlockSchemaRegistry reg) {
        List<ValidationError> errors = new ArrayList<>();
        if (state == null) {
            errors.add(new ValidationError(null, null, "EditorState 为 null"));
            return errors;
        }
        if (reg == null) {
            errors.add(new ValidationError(null, null, "BlockSchemaRegistry 为 null"));
            return errors;
        }
        for (EditorBlock b : state.getBlocks()) {
            validateBlock(state, b, reg, errors);
        }
        return errors;
    }

    private void validateBlock(EditorState state, EditorBlock b, BlockSchemaRegistry reg, List<ValidationError> errors) {
        BlockSchema schema = reg.get(b.schemaId());
        if (schema == null) {
            errors.add(new ValidationError(b.id(), null, "未知 schema: " + b.schemaId()));
            return;
        }
        // 字段校验
        for (BlockField f : schema.fields()) {
            validateField(b, f, errors);
        }
        // 子块 category 兼容 + 存在性
        List<String> accepts = schema.acceptsChildrenCategories();
        for (String childId : b.childIds()) {
            EditorBlock child = state.getById(childId);
            if (child == null) {
                errors.add(new ValidationError(b.id(), null, "引用了不存在的子块: " + childId));
                continue;
            }
            BlockSchema childSchema = reg.get(child.schemaId());
            if (childSchema == null) {
                // 子块 schema 错误会在子块自身校验时报告，这里跳过
                continue;
            }
            String childCat = childSchema.category().name();
            if (!accepts.isEmpty() && !accepts.contains(childCat)) {
                errors.add(new ValidationError(b.id(), null,
                        "父块 " + b.schemaId() + " 不接受 " + childCat + " 类子块 (" + childId + ")"));
            }
        }
    }

    private void validateField(EditorBlock b, BlockField f, List<ValidationError> errors) {
        Object val = b.fieldValues().get(f.name());
        if (val == null) {
            val = f.defaultValue();
        }
        // required 检查
        if (f.required() && isEmpty(val)) {
            errors.add(new ValidationError(b.id(), f.name(), "缺少必填字段: " + f.name()));
            return;
        }
        if (val == null) {
            // 非必填且无值，跳过类型校验
            return;
        }
        // 类型校验
        switch (f.type()) {
            case ENUM -> {
                String s = val.toString();
                if (!f.enumValues().isEmpty() && !f.enumValues().contains(s)) {
                    errors.add(new ValidationError(b.id(), f.name(),
                            "字段 " + f.name() + " 的值 " + s + " 不在合法枚举 " + f.enumValues() + " 中"));
                }
            }
            case RESOURCE_LOCATION -> {
                if (ResourceLocation.tryParse(val.toString()) == null) {
                    errors.add(new ValidationError(b.id(), f.name(),
                            "字段 " + f.name() + " 不是合法 ResourceLocation: " + val));
                }
            }
            case NUMBER -> {
                if (!isNumeric(val)) {
                    errors.add(new ValidationError(b.id(), f.name(),
                            "字段 " + f.name() + " 期望数字，实际: " + val));
                }
            }
            case BOOLEAN -> {
                if (!(val instanceof Boolean) && !val.toString().equalsIgnoreCase("true")
                        && !val.toString().equalsIgnoreCase("false")) {
                    errors.add(new ValidationError(b.id(), f.name(),
                            "字段 " + f.name() + " 期望布尔，实际: " + val));
                }
            }
            case STRING, TEXT_COMPONENT, BLOCK_REF -> {
                // 字符串类，任何值都接受其 toString
            }
        }
    }

    private boolean isEmpty(Object val) {
        if (val == null) {
            return true;
        }
        if (val instanceof String s) {
            return s.isBlank();
        }
        return false;
    }

    private boolean isNumeric(Object val) {
        if (val instanceof Number) {
            return true;
        }
        String s = val.toString().trim();
        if (s.isEmpty()) {
            return false;
        }
        try {
            Double.parseDouble(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
