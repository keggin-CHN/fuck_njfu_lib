package handlers

import (
	"context"
	"net/http"
	"time"

	"fucknjfu_lib/internal/models"
	"fucknjfu_lib/internal/utils"

	"github.com/gin-gonic/gin"
)

type reservationRequest struct {
	Area        string `json:"area"`
	SeatNumber  int    `json:"seatNumber"`
	SeatID      int    `json:"seatId"`
	ReserveDate string `json:"reserveDate"`
	StartTime   string `json:"startTime"`
	EndTime     string `json:"endTime"`
}

type cancelRequest struct {
	UUID string `json:"uuid"`
}

func (h *Handler) RegisterReservationRoutes(rg *gin.RouterGroup) {
	rg.GET("/history", h.getReservationHistory)
	rg.POST("/manual", h.manualReservation)
	rg.POST("/cancel", h.cancelReservation)
	rg.GET("/live", h.getLiveReservations)
}

func (h *Handler) manualReservation(c *gin.Context) {
	userValue, exists := c.Get("currentUser")
	if !exists {
		c.JSON(http.StatusUnauthorized, gin.H{"error": "未登录"})
		return
	}
	user := userValue.(models.User)

	var req reservationRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "参数错误"})
		return
	}

	history, err := h.createReservation(c.Request.Context(), user.ID, req)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}

	c.JSON(http.StatusCreated, history)
}

func (h *Handler) cancelReservation(c *gin.Context) {
	userValue, exists := c.Get("currentUser")
	if !exists {
		c.JSON(http.StatusUnauthorized, gin.H{"error": "未登录"})
		return
	}
	user := userValue.(models.User)

	var req cancelRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "参数错误"})
		return
	}

	if req.UUID == "" {
		c.JSON(http.StatusBadRequest, gin.H{"error": "缺少预约唯一标识"})
		return
	}

	result := h.DB.Model(&models.ReservationHistory{}).
		Where("uuid = ? AND user_id = ?", req.UUID, user.ID).
		Update("status", models.ReservationStatusFailed)

	if result.Error != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "取消失败"})
		return
	}
	if result.RowsAffected == 0 {
		c.JSON(http.StatusNotFound, gin.H{"error": "预约不存在"})
		return
	}

	c.JSON(http.StatusOK, gin.H{"message": "已取消"})
}

func (h *Handler) getReservationHistory(c *gin.Context) {
	userValue, exists := c.Get("currentUser")
	if !exists {
		c.JSON(http.StatusUnauthorized, gin.H{"error": "未登录"})
		return
	}
	user := userValue.(models.User)

	var histories []models.ReservationHistory
	if err := h.DB.Where("user_id = ?", user.ID).Order("created_at DESC").Limit(50).Find(&histories).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "获取历史失败"})
		return
	}

	c.JSON(http.StatusOK, histories)
}

func (h *Handler) getLiveReservations(c *gin.Context) {
	userValue, exists := c.Get("currentUser")
	if !exists {
		c.JSON(http.StatusUnauthorized, gin.H{"error": "未登录"})
		return
	}
	user := userValue.(models.User)

	today := time.Now().Truncate(24 * time.Hour)
	var histories []models.ReservationHistory
	if err := h.DB.Where("user_id = ? AND reserve_date >= ?", user.ID, today).
		Order("reserve_date ASC, start_time ASC").Find(&histories).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "获取预约信息失败"})
		return
	}

	c.JSON(http.StatusOK, histories)
}

func (h *Handler) createReservation(ctx context.Context, userID uint, req reservationRequest) (models.ReservationHistory, error) {
	reserveDate := time.Now().In(h.Config.SchedulerTZ).Truncate(24 * time.Hour)
	if req.ReserveDate != "" {
		parsed, err := time.ParseInLocation("2006-01-02", req.ReserveDate, h.Config.SchedulerTZ)
		if err == nil {
			reserveDate = parsed
		}
	}

	history := models.ReservationHistory{
		UserID:           userID,
		Area:             req.Area,
		SeatNumber:       req.SeatNumber,
		SeatID:           req.SeatID,
		ReserveDate:      reserveDate,
		StartTime:        req.StartTime,
		EndTime:          req.EndTime,
		Status:           models.ReservationStatusSuccess,
		Message:          "预约成功 (模拟)",
		UUID:             utils.NewUUID(),
		IsLateProtection: false,
		IsAutoFind:       false,
		CreatedAt:        time.Now(),
	}

	if err := h.DB.WithContext(ctx).Create(&history).Error; err != nil {
		return models.ReservationHistory{}, err
	}

	return history, nil
}

func (h *Handler) AutoReservationTask(ctx context.Context) {
	var users []models.User
	if err := h.DB.Preload("Settings").Find(&users).Error; err != nil {
		return
	}

	for _, user := range users {
		if user.Settings.AutoReserve {
			req := reservationRequest{
				Area:       user.Settings.Area,
				SeatNumber: user.Settings.SeatNumber,
				StartTime:  user.Settings.StartTime,
				EndTime:    user.Settings.EndTime,
			}
			_, _ = h.createReservation(ctx, user.ID, req)
		}
	}
}

func (h *Handler) LateProtectionTask(ctx context.Context) {
	cutoff := time.Now().Add(-30 * time.Minute)
	var histories []models.ReservationHistory
	if err := h.DB.Where("created_at >= ? AND status = ?", cutoff, models.ReservationStatusSuccess).
		Find(&histories).Error; err != nil {
		return
	}

	for _, history := range histories {
		// 模拟补签逻辑，实际项目应调用图书馆接口
		if time.Since(history.CreatedAt) > 15*time.Minute {
			h.DB.Model(&models.ReservationHistory{}).Where("id = ?", history.ID).
				Updates(map[string]interface{}{"is_late_protection": true, "message": "已自动签到"})
		}
	}
}
