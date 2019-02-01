package uk.gov.hmrc.bindingtariffclassification.crypto

import org.mockito.Mockito
import org.scalatest.mockito.MockitoSugar
import uk.gov.hmrc.bindingtariffclassification.config.{AppConfig, MongoEncryption}
import uk.gov.hmrc.crypto.PlainText
import uk.gov.hmrc.play.test.UnitSpec

class LocalCryptoSpec extends UnitSpec with MockitoSugar {

  private val config = mock[AppConfig]

  "encrypt()" should {

    "enable encrypt local" in {
      Mockito.when(config.mongoEncryption).thenReturn(MongoEncryption(true, Some("YjQ+NiViNGY4V2l2cSxnCg==")))
      (new LocalCrypto(config)).encrypt(PlainText("hello")).toString shouldBe "Crypted(gUfxIXsmMDAbdTgm36BmEg==)"
    }
  }

}
