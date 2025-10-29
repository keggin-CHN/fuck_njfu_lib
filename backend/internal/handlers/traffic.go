package handlers

import (
	"context"
	"encoding/csv"
	"net/http"
	"strconv"
	"time"

	"fucknjfu_lib/internal/models"

	"github.com/gin-gonic/gin"
)

func (h *Handler) RegisterTrafficRoutes(rg *gin.RouterGroup) {
	rg.GET("/latest", h.getLatestTraffic)
	rg.GET("/recent", h.getRecentTraffic)
	rg.GET("/export", h.exportTraffic)
}

func (h *Handler) getLatestTraffic(c *gin.Context) {
	var traffic models.Traffic
	if err := h.DB.Order("timestamp DESC").First(&traffic).Error; err != nil {
		c.JSON(http.StatusOK, gin.H{"count": 0})
		return
	}

	c.JSON(http.StatusOK, traffic)
}

func (h *Handler) getRecentTraffic(c *gin.Context) {
	hoursStr := c.DefaultQuery("hours", "24")
	hours, err := strconv.Atoi(hoursStr)
	if err != nil {
		hours = 24
	}

	cutoff := time.Now().Add(-time.Duration(hours) * time.Hour).Unix()
	var traffics []models.Traffic
	if err := h.DB.Where("timestamp >= ?", cutoff).Order("timestamp ASC").Find(&traffics).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "获取流量数据失败"})
		return
	}

	c.JSON(http.StatusOK, traffics)
}

func (h *Handler) exportTraffic(c *gin.Context) {
	var traffics []models.Traffic
	if err := h.DB.Order("timestamp ASC").Find(&traffics).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "获取数据失败"})
		return
	}

	c.Header("Content-Type", "text/csv")
	c.Header("Content-Disposition", "attachment; filename=traffic.csv")

	writer := csv.NewWriter(c.Writer)
	_ = writer.Write([]string{"timestamp", "count"})
	for _, item := range traffics {
		_ = writer.Write([]string{
			time.Unix(item.Timestamp, 0).Format(time.RFC3339),
			strconv.Itoa(item.Count),
		})
	}
	writer.Flush()
}

func (h *Handler) TrafficCollectionTask(ctx context.Context) {
	now := time.Now()
	entry := models.Traffic{
		Timestamp: now.Unix(),
		Count:     300 + int(now.Unix()%100),
		CreatedAt: now,
	}
	_ = h.DB.Create(&entry).Error
}
