package middleware

import (
	"net/http"
	"strings"

	"fucknjfu_lib/internal/config"
	"fucknjfu_lib/internal/models"
	"fucknjfu_lib/internal/utils"

	"github.com/gin-gonic/gin"
	"gorm.io/gorm"
)

type AuthMiddleware struct {
	DB     *gorm.DB
	Config config.Config
}

func NewAuthMiddleware(db *gorm.DB, cfg config.Config) *AuthMiddleware {
	return &AuthMiddleware{DB: db, Config: cfg}
}

func (m *AuthMiddleware) RequireAuth() gin.HandlerFunc {
	return func(c *gin.Context) {
		tokenString := extractToken(c.GetHeader("Authorization"))
		if tokenString == "" {
			c.AbortWithStatusJSON(http.StatusUnauthorized, gin.H{"error": "缺少认证信息"})
			return
		}

		claims, err := utils.ParseToken(tokenString, m.Config.JWTSecret)
		if err != nil {
			c.AbortWithStatusJSON(http.StatusUnauthorized, gin.H{"error": "认证失败"})
			return
		}

		var user models.User
		if err := m.DB.First(&user, claims.UserID).Error; err != nil {
			if err == gorm.ErrRecordNotFound {
				c.AbortWithStatusJSON(http.StatusUnauthorized, gin.H{"error": "用户不存在"})
				return
			}
			c.AbortWithStatusJSON(http.StatusInternalServerError, gin.H{"error": "查询用户失败"})
			return
		}

		c.Set("currentUser", user)
		c.Next()
	}
}

func (m *AuthMiddleware) RequireAdmin() gin.HandlerFunc {
	return func(c *gin.Context) {
		userValue, exists := c.Get("currentUser")
		if !exists {
			c.AbortWithStatusJSON(http.StatusUnauthorized, gin.H{"error": "未认证"})
			return
		}

		user := userValue.(models.User)
		if !user.IsAdmin {
			c.AbortWithStatusJSON(http.StatusForbidden, gin.H{"error": "需要管理员权限"})
			return
		}

		c.Next()
	}
}

func extractToken(authorization string) string {
	if authorization == "" {
		return ""
	}

	parts := strings.SplitN(authorization, " ", 2)
	if len(parts) != 2 {
		return ""
	}

	if !strings.EqualFold(parts[0], "Bearer") {
		return ""
	}

	return parts[1]
}
