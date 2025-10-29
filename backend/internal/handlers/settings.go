package handlers

import (
	"net/http"
	"time"

	"fucknjfu_lib/internal/models"

	"github.com/gin-gonic/gin"
)

type updateSettingRequest struct {
	Area         string `json:"area"`
	SeatNumber   int    `json:"seatNumber"`
	StartTime    string `json:"startTime"`
	EndTime      string `json:"endTime"`
	AutoReserve  bool   `json:"autoReserve"`
	PreventLate  bool   `json:"preventLate"`
	AutoFindSeat bool   `json:"autoFindSeat"`
}

func (h *Handler) RegisterSettingRoutes(rg *gin.RouterGroup) {
	rg.GET("", h.getSettings)
	rg.PUT("", h.updateSettings)
}

func (h *Handler) getSettings(c *gin.Context) {
	userValue, exists := c.Get("currentUser")
	if !exists {
		c.JSON(http.StatusUnauthorized, gin.H{"error": "未登录"})
		return
	}
	user := userValue.(models.User)

	var setting models.ReservationSetting
	if err := h.DB.Where("user_id = ?", user.ID).First(&setting).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "获取预约设置失败"})
		return
	}

	c.JSON(http.StatusOK, setting)
}

func (h *Handler) updateSettings(c *gin.Context) {
	userValue, exists := c.Get("currentUser")
	if !exists {
		c.JSON(http.StatusUnauthorized, gin.H{"error": "未登录"})
		return
	}
	user := userValue.(models.User)

	var req updateSettingRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "参数错误"})
		return
	}

	var setting models.ReservationSetting
	if err := h.DB.Where("user_id = ?", user.ID).First(&setting).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "获取预约设置失败"})
		return
	}

	setting.Area = req.Area
	setting.SeatNumber = req.SeatNumber
	setting.StartTime = req.StartTime
	setting.EndTime = req.EndTime
	setting.AutoReserve = req.AutoReserve
	setting.PreventLate = req.PreventLate
	setting.AutoFindSeat = req.AutoFindSeat
	setting.UpdatedAt = time.Now()

	if err := h.DB.Save(&setting).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "更新失败"})
		return
	}

	c.JSON(http.StatusOK, setting)
}
