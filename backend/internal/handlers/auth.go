package handlers

import (
	"net/http"
	"strings"
	"time"

	"fucknjfu_lib/internal/models"
	"fucknjfu_lib/internal/utils"

	"github.com/gin-gonic/gin"
	"gorm.io/gorm"
)

type registerRequest struct {
	Username         string `json:"username"`
	Password         string `json:"password"`
	EduPassword      string `json:"eduPassword"`
	LibPassword      string `json:"libPassword"`
	InviteCode       string `json:"inviteCode"`
	NotificationType string `json:"notificationType"`
	WebhookURL       string `json:"webhookUrl"`
}

type loginRequest struct {
	Username string `json:"username"`
	Password string `json:"password"`
}

type loginResponse struct {
	Token string      `json:"token"`
	User  models.User `json:"user"`
}

type apiResponse struct {
	Message string `json:"message"`
}

func (h *Handler) RegisterAuthRoutes(rg *gin.RouterGroup) {
	rg.POST("/register", h.register)
	rg.POST("/login", h.login)
	rg.GET("/me", h.me)
}

func (h *Handler) register(c *gin.Context) {
	var req registerRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "参数错误"})
		return
	}

	req.Username = strings.TrimSpace(req.Username)
	if req.Username == "" || req.Password == "" {
		c.JSON(http.StatusBadRequest, gin.H{"error": "用户名或密码不能为空"})
		return
	}

	var count int64
	h.DB.Model(&models.User{}).Count(&count)
	isFirstUser := count == 0

	var invite *models.InviteCode
	if !isFirstUser && h.getSystemSettingBool("invite_code_required", true) {
		if strings.TrimSpace(req.InviteCode) == "" {
			c.JSON(http.StatusBadRequest, gin.H{"error": "需要邀请码"})
			return
		}
		var inviteCode models.InviteCode
		if err := h.DB.Where("code = ?", req.InviteCode).First(&inviteCode).Error; err != nil {
			c.JSON(http.StatusBadRequest, gin.H{"error": "邀请码不存在"})
			return
		}
		if inviteCode.IsUsed {
			c.JSON(http.StatusBadRequest, gin.H{"error": "邀请码已使用"})
			return
		}
		invite = &inviteCode
	}

	var existing models.User
	if err := h.DB.Where("username = ?", req.Username).First(&existing).Error; err == nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "账号已存在"})
		return
	}

	passwordHash, err := utils.HashPassword(req.Password)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "密码加密失败"})
		return
	}

	eduEncrypted, err := utils.Encrypt(h.Config.EncryptionSecret, req.EduPassword)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "统一认证密码加密失败"})
		return
	}

	libEncrypted, err := utils.Encrypt(h.Config.EncryptionSecret, req.LibPassword)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "图书馆密码加密失败"})
		return
	}

	err = h.DB.Transaction(func(tx *gorm.DB) error {
		user := models.User{
			Username:         req.Username,
			PasswordHash:     passwordHash,
			EduPassword:      eduEncrypted,
			LibPassword:      libEncrypted,
			IsAdmin:          isFirstUser,
			NotificationType: req.NotificationType,
			WebhookURL:       req.WebhookURL,
			CreatedAt:        time.Now(),
			UpdatedAt:        time.Now(),
		}

		if err := tx.Create(&user).Error; err != nil {
			return err
		}

		setting := models.ReservationSetting{
			UserID:       user.ID,
			Area:         "默认区域",
			SeatNumber:   0,
			StartTime:    "08:00",
			EndTime:      "22:00",
			AutoReserve:  false,
			PreventLate:  false,
			AutoFindSeat: false,
			CreatedAt:    time.Now(),
			UpdatedAt:    time.Now(),
		}

		if err := tx.Create(&setting).Error; err != nil {
			return err
		}

		if invite != nil {
			invite.IsUsed = true
			invite.UsedBy = &user.ID
			now := time.Now()
			invite.UsedAt = &now
			if err := tx.Save(invite).Error; err != nil {
				return err
			}
		}

		if isFirstUser {
			_ = h.setSystemSetting("invite_code_required", "true", "是否启用邀请码注册", &user.ID)
		}

		return nil
	})

	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "注册失败"})
		return
	}

	c.JSON(http.StatusCreated, apiResponse{Message: "注册成功"})
}

func (h *Handler) login(c *gin.Context) {
	var req loginRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "参数错误"})
		return
	}

	var user models.User
	if err := h.DB.Where("username = ?", req.Username).First(&user).Error; err != nil {
		if err == gorm.ErrRecordNotFound {
			c.JSON(http.StatusUnauthorized, gin.H{"error": "用户不存在"})
			return
		}
		c.JSON(http.StatusInternalServerError, gin.H{"error": "查询失败"})
		return
	}

	if !utils.CheckPassword(user.PasswordHash, req.Password) {
		c.JSON(http.StatusUnauthorized, gin.H{"error": "密码错误"})
		return
	}

	token, err := utils.GenerateToken(user.ID, user.IsAdmin, h.Config.JWTSecret, 24*time.Hour)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "生成令牌失败"})
		return
	}

	c.JSON(http.StatusOK, loginResponse{Token: token, User: user})
}

func (h *Handler) me(c *gin.Context) {
	userValue, exists := c.Get("currentUser")
	if !exists {
		c.JSON(http.StatusUnauthorized, gin.H{"error": "未登录"})
		return
	}

	user := userValue.(models.User)
	var fullUser models.User
	if err := h.DB.Preload("Settings").First(&fullUser, user.ID).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "获取用户信息失败"})
		return
	}

	c.JSON(http.StatusOK, fullUser)
}
