package handlers

import (
	"net/http"
	"strconv"
	"time"

	"fucknjfu_lib/internal/models"
	"fucknjfu_lib/internal/utils"

	"github.com/gin-gonic/gin"
	"gorm.io/gorm"
)

type createInviteRequest struct {
	Count int `json:"count"`
}

type updateSetting struct {
	Key         string `json:"key"`
	Value       string `json:"value"`
	Description string `json:"description"`
}

func (h *Handler) RegisterAdminRoutes(rg *gin.RouterGroup) {
	rg.GET("/invite-codes", h.listInviteCodes)
	rg.POST("/invite-codes", h.createInviteCodes)
	rg.DELETE("/invite-codes/:code", h.deleteInviteCode)

	rg.GET("/system-settings", h.getSystemSettings)
	rg.PUT("/system-settings", h.updateSystemSettings)

	rg.GET("/logs", h.listLogs)
	rg.GET("/users", h.listUsers)
}

func (h *Handler) listInviteCodes(c *gin.Context) {
	var codes []models.InviteCode
	if err := h.DB.Order("created_at DESC").Find(&codes).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "获取邀请码失败"})
		return
	}
	c.JSON(http.StatusOK, codes)
}

func (h *Handler) createInviteCodes(c *gin.Context) {
	userValue, _ := c.Get("currentUser")
	user := userValue.(models.User)

	var req createInviteRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		req.Count = 1
	}
	if req.Count <= 0 {
		req.Count = 1
	}
	if req.Count > 20 {
		req.Count = 20
	}

	codes := make([]models.InviteCode, 0, req.Count)
	for i := 0; i < req.Count; i++ {
		codes = append(codes, models.InviteCode{
			Code:      utils.GenerateInviteCode(),
			CreatedBy: user.ID,
			CreatedAt: time.Now(),
			IsUsed:    false,
		})
	}

	if err := h.DB.Create(&codes).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "创建邀请码失败"})
		return
	}

	c.JSON(http.StatusCreated, codes)
}

func (h *Handler) deleteInviteCode(c *gin.Context) {
	code := c.Param("code")
	if code == "" {
		c.JSON(http.StatusBadRequest, gin.H{"error": "缺少邀请码"})
		return
	}

	result := h.DB.Delete(&models.InviteCode{}, "code = ? AND is_used = 0", code)
	if result.Error != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "删除失败"})
		return
	}
	if result.RowsAffected == 0 {
		c.JSON(http.StatusNotFound, gin.H{"error": "邀请码不存在或已使用"})
		return
	}

	c.JSON(http.StatusOK, gin.H{"message": "已删除"})
}

func (h *Handler) getSystemSettings(c *gin.Context) {
	var settings []models.SystemSetting
	if err := h.DB.Order("key ASC").Find(&settings).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "获取系统设置失败"})
		return
	}

	c.JSON(http.StatusOK, settings)
}

func (h *Handler) updateSystemSettings(c *gin.Context) {
	userValue, exists := c.Get("currentUser")
	if !exists {
		c.JSON(http.StatusUnauthorized, gin.H{"error": "未登录"})
		return
	}
	user := userValue.(models.User)

	var payload []updateSetting
	if err := c.ShouldBindJSON(&payload); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "参数错误"})
		return
	}

	err := h.DB.Transaction(func(tx *gorm.DB) error {
		for _, item := range payload {
			if item.Key == "" {
				continue
			}
			var setting models.SystemSetting
			if err := tx.Where("key = ?", item.Key).First(&setting).Error; err != nil {
				if err == gorm.ErrRecordNotFound {
					setting = models.SystemSetting{Key: item.Key}
				} else {
					return err
				}
			}
			setting.Value = item.Value
			setting.Description = item.Description
			setting.UpdatedBy = &user.ID
			setting.UpdatedAt = time.Now()
			if err := tx.Save(&setting).Error; err != nil {
				return err
			}
		}
		return nil
	})

	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "更新失败"})
		return
	}

	c.JSON(http.StatusOK, gin.H{"message": "已更新"})
}

func (h *Handler) listLogs(c *gin.Context) {
	limitStr := c.DefaultQuery("limit", "50")
	limit, err := strconv.Atoi(limitStr)
	if err != nil || limit <= 0 {
		limit = 50
	}
	if limit > 200 {
		limit = 200
	}

	var logs []models.LogEntry
	if err := h.DB.Order("created_at DESC").Limit(limit).Find(&logs).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "获取日志失败"})
		return
	}

	c.JSON(http.StatusOK, logs)
}

func (h *Handler) listUsers(c *gin.Context) {
	var users []models.User
	if err := h.DB.Preload("Settings").Order("created_at DESC").Find(&users).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "获取用户失败"})
		return
	}

	c.JSON(http.StatusOK, users)
}
