package com.ubirch.services.poc
import com.ubirch.models.auth.{ Base16String, CertIdentifier }
import com.ubirch.models.auth.cert.{ Passphrase, SharedAuthCertificateResponse }
import monix.eval.Task

import java.util.UUID

class TestCertHandler extends CertHandler {
  override def createOrganisationalUnitCertificate(
    orgUUID: UUID,
    orgUnitId: UUID,
    identifier: CertIdentifier): Task[Either[CertificateCreationError, Unit]] = {
    Task(Right(()))
  }

  override def createSharedAuthCertificate(
    orgUnitId: UUID,
    groupId: UUID,
    identifier: CertIdentifier): Task[Either[
    CertificateCreationError,
    SharedAuthCertificateResponse]] = {
    Task(Right(SharedAuthCertificateResponse(
      Passphrase("6C5D6NXSel71"),
      Base16String("3082165d0201033082162306092a864886f70d010701a0821614048216103082160c308214ef06092a864886f70d010706a08214e0308214dc020100308214d506092a864886f70d010701301c060a2a864886f70d010c0106300e0408f6e37a2b1f6a8c2802020800808214a8b36e7a23a87e37d71f5925ef3a5205ff986c4feb937ee1a216869d1f31319f9ec2431cefa25bcf9533de66eddc44b01dfdb2c98029edda6a77d543a2a2526e04eebc9f9b22173b5d6f0bd0ef81d539a00f3172899ade64735ddd4c4c9f79aaa9b6fef2e65aacd59ced153528175949af6d59833e0bf633216bea114f73089a887b9b10949cb17b7216c6e77f3d25ab43cdc0022e68a34f054055c9ada580f5295cb82725a2c05eca82d2719d4b7fa11440b0672dbe4e21f669ccc8919af864c90aa4eab5aaad62e3ac08163816cd770f26ad92b3c5161851b71569f342819ab4a5d4a3388db8b3621bba8d09c2e9cf1b81691595b054a43c2c9ba59c40309701ce7e0f72730eabf7cb845cd00af519de671e37668194d2feb96ff93fe7b67d76054ed22a80a929305c48c2fba553e53087dd2f03f40dd2da48b1dcf5b427a8a5f56626ee7da2633d373473c3af516ec364256ef75e853fad67df23d60c6d9a0bd2df335d902118d2702dc9fe9fb4c76f9a77ba0f436cfe7461b72e126c11658a039c3e0c9c0088eafc6b227f1331e7c0405eada9ecfce1045a42ade13f64cdfc184fecf3b87c6498664a88a37f15fc188cf5f38438335b4397096bc5dee570820064df852d8654762369067b9b5a0c79b6df2cc2ac683a3a1a4f230918c39e350ebe8c153ff30053943cd79aad1cbf7235e66dba4214f5091b4fc9a3c2301c2d84433889f8f447c44c862c067b7edcbb0f52c3dc8b01011d346b406885956c5627f64e1e939eacbd8770a5cb4b5692914f640029e2e3d21376f12b9e9c3c7f13ac944cb54430f170badcafe5747c09279cf685078e0bbf0454f733317e2e624a8267bb8b94ec942e9ffc9a20049667387705ec6a4e42559e404b48ee008b5f08a7f3e83675175962b730e323285039447c852fb9ac2e4e8329dce5350ac133272cc8f488a5abf0bb6ade68a532750f273130db28175a23c92c9765223102d0320ce457d50369a6450e1de634994b180afb206d72aba255c8300488c9f61046749533e40a0bd1cc7c3712d4276ad431b7d45e2ecb5f4eab75c671095e2913f78e5904f945282d3c51bee430a1186283ce4cd886013fb701426ee937a01bc029f3d1ba00c51a85ef03371b723a3be1c66b6568a3a933f5fd69076c5bd13c208327b80e2002695fe42feb90b6fb320c72c6b17720deea4408db9d284df393d6bb029be07728d100c64bb3981153a6336d0c1ac2ef0b7c52b1b27a33bf9c8c507503c439cc70dd28469ef66ae6fefe3955582a264ccea55b7664109d7b88ce065a12979431a7a8a8249a02afc29e1944f3a7b8820e75f67913269f5785c7bb108bbe154141347acaf5988d7594505a2ec3e2a1d812edf5a6240196ea7c59983278bddf2be4b60fe0eec8e730eff43c2db4b8022d7b68b192831f9b6d8d97c7c3bfc058ed2024b9e640a2ed51cb8d84c5a86161d92d17f7bba3df1be9d04e6510827871d6b8db34653e15ce6f7f6eaf5e0ca9574e31ba53d75bfc5f91051d4914cef5949caf607870be57f44303cbbec3d1fdbe27d61d920833e90d30c0e841324dfbb021b0da7f59c9b907a2f302b1f6f6a11c95eb08ce78f40f45a227b76ef99a7c092c7d90a6e7578d26c5a6b9d37d24155389bf5bb22d2efec74162f25116d5507c6bb221f75c73e41cd6d8b4e235b3c59b8268c0089a2b88699b3caa761c7e63082fda0db3e88a07b528212dbd524eb939e8bb0520ec3bd01986bf1baea9fa084fa0553d55ce7ead30f4d57a2d7c811967d41592392ed034008ef3beb007b87351384d9bf1ce50d65cc552a1fe833a168f0ccd2a7a8c8582166a4bd3941aea72820fef8cc4a04d66ee13502cded9677b09e30837b608f497f56240fd6c5ef3b1a67b134285e29962aef2f856fdf0c0d055800aeef6fd88d2b73cd64490ee825423746cda1802481c017884278e6db6aa21420ab6def782b7874feeef2fc0af69aef0243743cf12b63ee886fbcbf1ce2321668724d57ba4ea970faa9a4d0c2e4cd5fc6cfef28c549c75849cc3ff295fe0b0e30d5398dfd2cfa65dce51e2410f9e9c20f4d0d16342d1976de911dc666a4ed9ae7553d0d9eb17f9b47afa1862cb1034d1d08f2cc1b1a35e832e55450673718508e1a48437967773eadceba0e491e537f14728dc7c63d65b3a5d5361490781b9ce99b29876bf501f7186c2ca5cfa810b4d6eea9363e0ff220e7aeab51e23336f2f5048ce0297181d77fb1768537995155f1c6d255f062de3c9822a89d05515b83cba7537393041ca96cb8be9315f163057e1e75ad4247890e7cd35c4e62a34f3d2a64658f3d0ec61d030b5db13704a87ef7461c0002ab8924c13a9fbbe0e17d10621bae32c1f6f36df1eb0e8a0fd3fbf825beddefcf4606d1dd17c106d1092c4e12909a6a26941aa36d6e2bb3ef5fc6a42129c47c37e13b6740beb7181f05751fe71a0e5ee88dcbaa02d74dd3c460d666d8e2980f6687d81c01b9912f94910106d44adaefa3c71e0684b6e0bf48831bb49c3ef6d7a13c519b9bab87e2c0b6aed895a02001dd07696030f36cfa06b72915b51a05149972d9ee6c6bd4fa26944955cf1e7aa0b38d2d3601dafab7b87464f7446cae5506dc9054438718517bd1383f9c40928c30f609c1bec3e82c61871c34e94447cc9ba81683c8b85a09ccdaa5add5c278443569aadc702fb3c7c7d5d2b22252c089f99e587ab3b23629c57abd5aaee4ad995792a9c9b09ee18d6bbd08cdb7dd25b0a277a8aec6ddb1286bc31c6ff34f32a1586aae00038defdccb7a9decd2daf1e158be5a3f8e3baf2464b6142bd107416f81eca2123afe4df32596a1fe254909195d53466ee2892914c17f3febd111eba2bd75a7f659e57f41272326b70b06866d50bc690ddd42dfc9e5eb2f93503f9aa3a59e3b4257d94bd35c23bbee68e10bd76db66a5b62890b0d0f531147e5efec333d8a4290d7cf8ae3412f4152027aec7f28330026449fd26575d44a01d9c05205283760707fb490e9ba9925cde8e1e48a16e896a062404121ddb80430d61a312eace0b418b3c2d487f8e43debff448d0f316b0307b3eaa3902cb90b4eb4ae838e1fb0759096379c465faf058b728a55597b9fe02007bde377d77e40215e8bab5c73a2fd4ffb452d05f66fc53d77ce92d1a405a02271a2078ee80073163317bdde63e428ebf7ea4abdaf271d76e70dca9516e9050114d24a57dc5c8f21470d26111e153408f3290923aed1d11694346f837735aef524d2fd36aed1dbdf01d2bee81ed9c5b10261869f1889b9d708598db1408ba2acf68ae6292c47df612da4e30f7febaf4e9c97334ae48ca54c650249239bf1f76582d64d32d8dbda51481e4346258d0386c553605a3027a9921984c8a8f80947242939ee9a83d2925177afdf784f4acb3cf93716aece15bbf652e55283989c2e013d39795f46161e603fedff6592d53c23306118e85de66873ab2a94c5d54e7227e7b69eacbc5ebf23241fdcdcbe6d92662a57bb77fa55f465916a72355bf7d7a3da3ca93019aecee8428a2523f76289047cf7e55a0f9ca9e2eddf338a1c231f9b7c2e7f6d551584d3c0d22e083e1461dd0ba307e0199c360f5c848505825b915fc99a0910f38adbd65fa0002453c559d28dde06d8abba46b94574e231b9171f90e94ac9140465a58abda8a89a901170fffeca5cae35be818e1308f85c1c10506452af278ed9dd597dd7c2485983f0e4464a83bb841cc0fb66a6e4bddfe180e5f62091bbd2f1ac73e6a76036c8ec65cb59e48375702754cba1daca123bc4e9e35536b5a5dbf773b4c74c9d6ce0059e22b0c803b98efd86bb39e3536e8c463b650ed048d5291872270618fe2632b64282facd03a4daa2946ec236422e7f519881f3c5e00fee14603359347903667a7075a141f1974ca69c6452c75e4d873f708174d5e6490dcea27fbe9f073c8bf9f075b0f45e458760e0e81eabd90e5618e8090d955c1939cb8a1175b8bdbe58706cedc10c83500209d9785850324afc894bad9a64f07cea2627975016aa6934db9b94911577c053417594a62ec600ac4fc65ed0d8b9ebba22676e13f671a716f27e313c25cb64167eb4b6e3f5eaace8a016e5f118c771adb08d9c1f58bc9d685c703437b2a1bc004bf42f3fd3e776b9baf28d265633623eea775e0dfffd5e79e7136789f012597a1e7668fa0e40686556ef102ebefd28c06045eac2f186662574110c43308280d0bc9a6422760541a1410d4fcf41a4170816709072dbec6784e2de63d8931ba5fe9b8b7fe2ccc32aae1dc89b54c10ea0f89f235dd0fe074c4d84ae1b0d767128b6078001b48dffa8c9fe5d0afb62d085209dd5badd7360d1adbd3e5e9957cb25f4d1f557fc5baf1cd0ff360187af4ff6046e6cfb7652935830230d09c87223d036f4a9b1a29e3c6fbb8a15087e9208e9f08d6502fe849058531420f257f536c351880b89554883e2395d109dba69071b1197c1b6c562456a188ea0b75c3574d3e7cdea0589b7c3000e7f16dbc07f1881ae9ac4368507a9a443f27a6aef16407569cac65a0eeb905639208cbdbd6de4e10095cbef0b197850939db4b7ff8246b9dab21181e96793eeda31b827d98d89ff676a0f584ba04339a122f555f958b34bdbea4993f8bd0167f572024f1f596c7100a6f13466db0451b9266d22f9ec3fbbbc0354b1d569379e138c67c46c982d1bccf38268e45cb9dd6a7128eff04f556165e96863a1e589cdb6cf12efd08603f87f698d3f37d580e9c9dddd31ca0b51ece8f070688fdc79def6bf6fc0192bb11d8b64e579c54823fa8f1e8f0de37508a183e8bce7de48b255e165da450fefa8d16d091c83b0f0e49c4ad4baec93acffa3f17d7c2b3ad640e7c783c855720622afea7b5e6594029ffdb194c5ee7a145c610bf9206b27eb123bfef1852ec8119afe4cebdaa438671e16b39b661d015edc6870bbd8b863f4025a7f42dbe58838d3b85d2e76efdffa43c828d8f253defd7e6ce204f261fa0638a36fa3549aa631aa6908278f33dfe8ba72e65c81d1b62d337fa6c68ecc8d2bb292367cfebdf29a7b13b3e4bdee1a211eef67a0c1394ac027ab5e99415e5b6a90d4cfbfb1d0c3afeae00deb78647fbe490816732a8048f246cf061c75fe343d67e76ca96623804ae0e4ec73d3f4f8b5f683685b441b3ff919ef9fac020181e255b8b223bc1d34689fd2397502d23ea640b16743aed4686dd3004afa6a445cc145ae07fe30355d3886f8a042a4c9bedd66c410750f543cf5b8455e33aed70fde3b6845af4b56ae5eac5e84cff281781ff2562348bc56206c4105f47f35dd4f53e6c6e9d3f1b61cfd5872d48011a9231e1a7b566d0e2ee482b73391ef7b2ba872c132c6f30eaabbda8982365074ad44341fb76c3520f53cd3c9be4755cebf06ee96c46ca88ed9ddf6f407eff44aa3571330d4aff6f43b9fcdce30b450cc8a0f7c13d0fbb95b481f0e5b2d3676cdcf2f287e9a0bdbb01aad615a42efb973d95912fea88cc066d53b53315d8a813ee364b4c811079a84df4282b6c7c52ba002328b1e51dcc65687aea954c41606e357aaf1d1cb16ff504648966955980029b54958a9b181c5fdafe2ab4bc65248dc816f33fdcb5ed0632308f828a7b62995ff82fb8f68baf335f9694d02dc760a3b141aa159b147705ba29f8f101876c218652faa803ea99dc0f07bbb0854555e14de42a3ba4af94f0bfe97a2c8890c210252a013c57b392060d5ceb8fbd6e072cee452683f146a2f17de0bc0138eb1b5b6b657a7e56430421e098c995369e1528b241475af10cc349d6be9d56239f4ab67357293f1ead39a6e48632c9b34a0b6a9e562f4798df3fdf6063003911949f084d4af6e4f12054480494cdf4fa76b91c8bf897c322a72527ac63301fc46d4d3ee57b3c4ac531e91c5e98cca0d92238cb51e9717c97ed3bbd0c7e0487d8af7993681a4567a205d241df6592a4f7acf079a83b6c8bf471f063b08ed43114d0bd4d04408af40e9119fc9a34c1968fac2092feb7d76237de5e60053187303b5122d45f23c683229b714e9b7ddd5974a6262a998ad2f6bbb06a31d32bc90cd15c1f34f75679f7c75f23425e6d4f069d5361c405d6b723f2c58cd3e7c540985ddc1665f6c02ef371d9e3836683752275ad13ef865c9d020da4be272169497ce441beab9dc3b9cabb1d22637c3b98245dcd9f3a313eb14748fab4fcd61ebedef543007f90c4377d9d48384f4de3ba3495d9f310f6a36d2f837eb3cf75b020516b045ad8288822560074738b799bf73e45b4277418f1f054faafa341dab0a9baec6c64ba9b4a14433810d64c05af5bd60af97ebb8dd681957630b72bd18e14cc1da55a07e0e71ddbb33f368302b6284371768ded47f1ac613e2e674861a0834ff5f7c77a910c8a025d7755b82c3489f5f4f60f8866ee615ba6fa9cd016a3d06dab56be8ef30bc14f4676327b8407ff483034fc45bdf0776cb8b0a3e0c70f970d3057fddecde1597af9448277e4546ca8a05f304a9028417812d146ca67771e114adb2333762b232efb423d9664bc1f75b7cd428f2f160209561ffa197ab4746de18d89c6efa31bada0469a1ca5d76b9cd3484788ec9773bc1f1d8e9a6e75d53e4c7e0863fb6ae8472fd00cb2e6171d150222f9696c2cb75be2aa7fc2d3510f518e59e03ae3e2ccc435443f9193f345ec060be57be3362ea298b2ed4c32c532aebd9564516769200bb827499875c478b64772ca458dd60d7153b99a53eed9d60c32035207ffe472c3a4e844564202697dad71a70949b7279886c324b044ab6326d121c954f6244e3057205eac8e47c7b6fa55add2ad6c3549d26a39f60fac8bab1f38a98eb348782ceffa5415c7bc92c5a3e9f2c5a380f34b430b2680993cf473e85d0162e7670b06bd75c4713eb10a860e7eeb2c5355c2bcdbea57074c718cbe5a74f49c36e878c2733680e424ef50386c81bfe3ef8a1daac51f44900fe4a6a782e5e4b2abf0a35bc84d21cffb193e64b701628e3992296135a0b94be285e1286244b330034c9c1e9018a247d09349656a9e4c5af66918e66c868518039bd9b2c11911fe3a527fc2e02ce6e97284445ce06667bad369554ff531ce1334a69dfba5e2959f316198d32bcb80bd8da348f4400e3cc3efca2929845a6f3e13fcdfa95022e961c443bb35bc004801c889b30256d7fd2536f815bb6a402836b838eb1dde03b70bb1d26a594f5faacd87972f9aa110d9c91276fe105f8de626cee9f417cadb8a2ca2c340594ee45efbff2e385675526ea6df06e82b396317bb4cd02167ea6eb26356715f2617fbdce1a7ce982c63222711eb7bf936e77066e5e7d555e3025c02240548f92cd5865587b6fcf7b97fd82592a6ae809047dd15463a164c963bbd2f0f863c11996833e9fb7b24ac2efcbffb614b569486f9ad3ccd3d5ecc43709c3db94916e2b01a7a3b4b753082011506092a864886f70d010701a0820106048201023081ff3081fc060b2a864886f70d010c0a0102a081b43081b1301c060a2a864886f70d010c0103300e0408d739fa132bab1ec802020800048190d40da85ad902ded8dcd1f2c11b29742071959280943d96231dfc625f81768b6fa4f12dd58f171e6ca607a3fc6c3de3f284e456b08f3ef53a924bc951749d8df3d7469efcce6800ac7b16add6d98d34d2791f20e6c959eb1486af639b4d9408d5700812b4b092620fe11e1259dd04fa2b1920f45f22051993c7f1571594f73c9235b76393860c9851e1726d8609cc67323136300f06092a864886f70d01091431021e00302306092a864886f70d010915311604143b22c7117a247606cf0f8e140b020828b71dbe1a30313021300906052b0e03021a05000414ce4acc7bda8143ad1bd7efa6edaad70b127716f50408f30738c1b38ff14b02020800")
    )))
  }
}
