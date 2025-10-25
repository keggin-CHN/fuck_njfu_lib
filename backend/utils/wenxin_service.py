import requests
import logging
import random

logger = logging.getLogger(__name__)


class HitokotoService:
    """一言（Hitokoto）API服务"""

    # 一言API端点
    HITOKOTO_URL = "https://v1.hitokoto.cn/"

    # 备用的随机问候语（当API不可用时使用）
    FALLBACK_GREETINGS = [
        "📚 读书是灵魂的旅行，愿今天的你收获满满！",
        "🌟 知识的海洋无边无际，每一次学习都是一次成长。",
        "💡 专注是成功的关键，加油！",
        "🎯 今天也要元气满满地学习哦！",
        "🌈 书籍是人类进步的阶梯，继续前进吧！",
        "⭐ 学习使人进步，坚持让梦想成真。",
        "🚀 每一次翻开书页，都是在探索新的世界。",
        "🌸 静心阅读，感受文字的温度。",
        "🎨 知识改变命运，学习成就未来。",
        "🌺 在图书馆的时光，是最美好的回忆。"
    ]

    @staticmethod
    def get_hitokoto():
        """
        获取一条一言。
        返回格式：句子内容 ---- 出处
        """
        try:
            response = requests.get(HitokotoService.HITOKOTO_URL, timeout=10)
            response.raise_for_status()

            result = response.json()
            hitokoto = result.get("hitokoto", "")
            from_source = result.get("from", "")

            if hitokoto:
                greeting = f"{hitokoto}    ----{from_source}"
                logger.info(f"一言获取成功: {greeting}")
                return greeting
            else:
                logger.warning("一言API返回空结果，使用备用问候语")
                return random.choice(HitokotoService.FALLBACK_GREETINGS)

        except Exception as e:
            logger.error(f"一言获取失败: {str(e)}")
            return random.choice(HitokotoService.FALLBACK_GREETINGS)

    @staticmethod
    def generate_greeting():
        """
        生成随机问候语（兼容原有接口）
        """
        return HitokotoService.get_hitokoto()
