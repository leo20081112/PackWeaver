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
 * 每个 ValidationError 同时填充中文 friendlyMessage 与 fixSuggestion。
 */
public final class BlockValidator {

    /**
     * 校验整个编辑器状态。
     * @return 错误列表（空表示通过）
     */
    public List<ValidationError> validate(EditorState state, BlockSchemaRegistry reg) {
        List<ValidationError> errors = new ArrayList<>();
        if (state == null) {
            errors.add(new ValidationError(null, null, "EditorState 为 null",
                    "编辑器状态为空，无法校验", "请先打开或新建一个数据包编辑会话"));
            return errors;
        }
        if (reg == null) {
            errors.add(new ValidationError(null, null, "BlockSchemaRegistry 为 null",
                    "积木注册表为空，无法识别积木类型", "请确认编辑器已正确初始化积木注册表"));
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
            errors.add(new ValidationError(b.id(), null, "未知 schema: " + b.schemaId(),
                    "此积木使用了编辑器无法识别的类型：" + b.schemaId(),
                    "删除该积木，或升级编辑器以支持此类型"));
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
                errors.add(new ValidationError(b.id(), null, "引用了不存在的子块: " + childId,
                        "父积木引用了一个已不存在的子积木：" + childId,
                        "重新连接子积木，或删除该悬空引用"));
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
                        "父块 " + b.schemaId() + " 不接受 " + childCat + " 类子块 (" + childId + ")",
                        "此积木不能放在该父积木下：" + childCat + " 类积木不被 " + b.schemaId() + " 接受",
                        "将该子积木移到接受 " + childCat + " 的父积木下，或更换父积木"));
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
            errors.add(new ValidationError(b.id(), f.name(), "缺少必填字段: " + f.name(),
                    "字段 " + f.name() + " 为必填，请填写",
                    "在积木编辑器中为字段 " + f.name() + " 填入合法值"));
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
                            "字段 " + f.name() + " 的值 " + s + " 不在合法枚举 " + f.enumValues() + " 中",
                            "字段 " + f.name() + " 的值必须是 " + String.join("/", f.enumValues()),
                            "从下拉列表中选择一个合法枚举值"));
                }
            }
            case RESOURCE_LOCATION -> {
                if (ResourceLocation.tryParse(val.toString()) == null) {
                    errors.add(new ValidationError(b.id(), f.name(),
                            "字段 " + f.name() + " 不是合法 ResourceLocation: " + val,
                            "字段 " + f.name() + " 必须是 命名空间:路径，仅小写字母/数字/下划线/连字符",
                            "检查是否包含大写字母、空格或其它非法字符"));
                }
            }
            case NUMBER -> {
                if (!isNumeric(val)) {
                    errors.add(new ValidationError(b.id(), f.name(),
                            "字段 " + f.name() + " 期望数字，实际: " + val,
                            "字段 " + f.name() + " 必须是数字",
                            "填入一个合法的数字，如 1 或 1.5"));
                }
            }
            case BOOLEAN -> {
                if (!(val instanceof Boolean) && !val.toString().equalsIgnoreCase("true")
                        && !val.toString().equalsIgnoreCase("false")) {
                    errors.add(new ValidationError(b.id(), f.name(),
                            "字段 " + f.name() + " 期望布尔，实际: " + val,
                            "字段 " + f.name() + " 必须是 true 或 false",
                            "填入布尔值 true 或 false"));
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
