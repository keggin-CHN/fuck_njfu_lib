package handlers

import (
	"strconv"

	"fucknjfu_lib/internal/models"

	"gorm.io/gorm"
)

func (h *Handler) getSystemSetting(key string, defaultValue string) string {
	var setting models.SystemSetting
	if err := h.DB.Where("key = ?", key).First(&setting).Error; err != nil {
		if err == gorm.ErrRecordNotFound {
			setting = models.SystemSetting{Key: key, Value: defaultValue}
			_ = h.DB.Create(&setting).Error
			return defaultValue
		}
		return defaultValue
	}
	return setting.Value
}

func (h *Handler) setSystemSetting(key string, value string, description string, userID *uint) error {
	var setting models.SystemSetting
	err := h.DB.Where("key = ?", key).First(&setting).Error
	if err == gorm.ErrRecordNotFound {
		setting = models.SystemSetting{Key: key}
	} else if err != nil {
		return err
	}

	setting.Value = value
	setting.Description = description
	if userID != nil {
		setting.UpdatedBy = userID
	}
	return h.DB.Save(&setting).Error
}

func (h *Handler) getSystemSettingBool(key string, defaultValue bool) bool {
	strVal := h.getSystemSetting(key, strconv.FormatBool(defaultValue))
	result, err := strconv.ParseBool(strVal)
	if err != nil {
		return defaultValue
	}
	return result
}
