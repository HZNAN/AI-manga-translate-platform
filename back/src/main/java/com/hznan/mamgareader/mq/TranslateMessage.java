package com.hznan.mamgareader.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TranslateMessage implements Serializable {
    private Long recordId;
}
