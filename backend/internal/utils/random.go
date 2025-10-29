package utils

import (
	"crypto/rand"
	"math/big"
)

const letters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"

func GenerateInviteCode() string {
	codeLength := 8
	result := make([]byte, codeLength)
	for i := range result {
		n, err := rand.Int(rand.Reader, big.NewInt(int64(len(letters))))
		if err != nil {
			result[i] = letters[i%len(letters)]
			continue
		}
		result[i] = letters[n.Int64()]
	}
	return string(result)
}
