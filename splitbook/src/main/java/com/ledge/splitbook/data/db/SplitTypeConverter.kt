package com.ledge.splitbook.data.db

import androidx.room.TypeConverter
import com.ledge.splitbook.data.entity.SplitType

class SplitTypeConverter {
    @TypeConverter
    fun fromString(value: String?): SplitType? = value?.let { SplitType.valueOf(it) }

    @TypeConverter
    fun toString(type: SplitType?): String? = type?.name
}
