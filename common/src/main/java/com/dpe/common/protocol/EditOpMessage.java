package com.dpe.common.protocol;

/**
 * 编辑操作消息。
 * op 取值：add / remove / move / connect / disconnect / field / text
 * value 为操作值（可为 String / Number / Boolean / 复合对象）。
 */
public record EditOpMessage(String op,
                            String blockId,
                            String field,
                            Object value,
                            String playerId) implements Message {
}
