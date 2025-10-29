package middleware

import (
	"time"

	"fucknjfu_lib/internal/models"

	"github.com/gin-gonic/gin"
	"gorm.io/gorm"
)

func RequestLogger(db *gorm.DB) gin.HandlerFunc {
	return func(c *gin.Context) {
		start := time.Now()
		c.Next()

		latency := time.Since(start)
		status := c.Writer.Status()

		var userID *uint
		if userValue, exists := c.Get("currentUser"); exists {
			user := userValue.(models.User)
			userID = &user.ID
		}

		entry := models.LogEntry{
			UserID:       userID,
			IPAddress:    c.ClientIP(),
			Action:       c.Request.Method + " " + c.FullPath(),
			UserAgent:    c.Request.UserAgent(),
			ResponseCode: status,
			ResponseBody: latency.String(),
			CreatedAt:    time.Now(),
		}

		_ = db.Create(&entry).Error
	}
}
