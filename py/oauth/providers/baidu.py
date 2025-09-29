"""
Baidu OAuth提供商实现
"""

from typing import Dict, Any
from ..base import OAuth2Provider
from ..exceptions import TokenError, UserInfoError


class BaiduProvider(OAuth2Provider):
    """Baidu OAuth 2.0提供商"""

    def get_provider_name(self) -> str:
        return "baidu"

    async def get_access_token(self, code: str) -> str:
        """获取Baidu访问令牌"""
        try:
            response = await self.handle_http_request(
                "POST",
                self.config["token_url"],
                data={
                    "grant_type": "authorization_code",
                    "code": code,
                    "client_id": self.config["client_id"],
                    "client_secret": self.config["client_secret"],
                    "redirect_uri": self.config["redirect_uri"],
                }
            )

            token_data = response.json()
            access_token = token_data.get("access_token")

            if not access_token:
                raise TokenError("Baidu未返回访问令牌", "no_token", "baidu")

            # 存储refresh_token备用
            self._refresh_token = token_data.get("refresh_token")
            return access_token

        except Exception as e:
            if isinstance(e, TokenError):
                raise
            raise TokenError(f"获取Baidu访问令牌失败: {str(e)}", "token_request_failed", "baidu")

    async def refresh_access_token(self, refresh_token: str) -> str:
        """刷新访问令牌"""
        try:
            response = await self.handle_http_request(
                "POST",
                self.config["token_url"],
                data={
                    "grant_type": "refresh_token",
                    "refresh_token": refresh_token,
                    "client_id": self.config["client_id"],
                    "client_secret": self.config["client_secret"],
                }
            )

            token_data = response.json()
            access_token = token_data.get("access_token")
            if not access_token:
                raise TokenError("Baidu未返回新的访问令牌", "no_token", "baidu")

            self._refresh_token = token_data.get("refresh_token", refresh_token)
            return access_token

        except Exception as e:
            if isinstance(e, TokenError):
                raise
            raise TokenError(f"刷新Baidu访问令牌失败: {str(e)}", "refresh_failed", "baidu")

    async def get_user_info(self, access_token: str) -> Dict[str, Any]:
        """获取Baidu用户信息"""
        try:
            response = await self.handle_http_request(
                "POST",
                self.config["user_info_url"],
                data={
                    "access_token": access_token,
                    "get_unionid": 1
                }
            )

            user_info = response.json()

            if user_info.get("error_code"):
                error_msg = user_info.get("error_msg", "未知错误")
                raise UserInfoError(f"Baidu返回错误: {error_msg}", "user_info_failed", "baidu")

            # Baidu 返回 data 集合
            uid = user_info.get("uid") or user_info.get("openid")
            displayname = user_info.get("uname") or user_info.get("username")
            portrait = user_info.get("portrait")
            avatar = f"https://himg.bdimg.com/sys/portrait/item/{portrait}.jpg" if portrait else ""

            # Baidu的接口可能不直接返回邮箱，需要后续收集
            processed_email, email_collection_needed = self.check_email_collection_needed(user_info.get("email"))

            return {
                "provider": "baidu",
                "uid": str(uid or ""),
                "username": displayname or "",
                "email": processed_email,
                "avatar": avatar,
                "email_collection_needed": email_collection_needed,
                "raw_data": user_info
            }

        except Exception as e:
            if isinstance(e, UserInfoError):
                raise
            raise UserInfoError(f"获取Baidu用户信息失败: {str(e)}", "user_info_failed", "baidu")
